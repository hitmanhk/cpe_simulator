package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ReconnectScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconnectScheduler.class);

    private final Set<String> pendingDevices = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, BackoffTracker> backoffTrackers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Function<String, XmppConnection> connectionProvider;
    private final EventLoopGroup eventLoopGroup;
    private final long initialDelayMs;
    private final long maxDelayMs;

    public ReconnectScheduler(ScheduledExecutorService scheduler,
                              Function<String, XmppConnection> connectionProvider,
                              EventLoopGroup eventLoopGroup) {
        this(scheduler, connectionProvider, eventLoopGroup, new AppConfig.XmppConfig());
    }

    public ReconnectScheduler(ScheduledExecutorService scheduler,
                               Function<String, XmppConnection> connectionProvider,
                               EventLoopGroup eventLoopGroup,
                               AppConfig.XmppConfig config) {
        this.scheduler = scheduler;
        this.connectionProvider = connectionProvider;
        this.eventLoopGroup = eventLoopGroup;
        this.initialDelayMs = TimeUnit.SECONDS.toMillis(config.getReconnectInitialDelay());
        this.maxDelayMs = TimeUnit.SECONDS.toMillis(config.getReconnectMaxDelay());
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("xmpp.reconnect-initial-delay must be greater than 0");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException(
                "xmpp.reconnect-max-delay must be greater than or equal to xmpp.reconnect-initial-delay");
        }
        log.info("XMPP reconnect backoff configured: initial={}s, max={}s, multiplier=2, jitter=10%",
            config.getReconnectInitialDelay(), config.getReconnectMaxDelay());
    }

    public void onFailure(String deviceId) {
        if (!pendingDevices.add(deviceId)) {
            log.debug("Reconnect already pending for device {}, ignoring duplicate", deviceId);
            return;
        }
        BackoffTracker tracker = backoffTrackers.computeIfAbsent(
            deviceId, k -> new BackoffTracker(initialDelayMs, maxDelayMs));
        long delay = tracker.nextDelay();
        log.info("Scheduling reconnect for device {} in {}ms (attempt {})",
                 deviceId, delay, tracker.getAttemptCount());
        scheduler.schedule(() -> reconnect(deviceId), delay, TimeUnit.MILLISECONDS);
    }

    public void onSuccess(String deviceId) {
        BackoffTracker tracker = backoffTrackers.remove(deviceId);
        if (tracker != null) {
            log.debug("Removed backoff tracker for device {} after successful reconnect", deviceId);
        }
    }

    private void reconnect(String deviceId) {
        pendingDevices.remove(deviceId);
        XmppConnection conn = connectionProvider.apply(deviceId);
        if (conn != null) {
            log.info("Attempting reconnect for device {}", deviceId);
            conn.connect(eventLoopGroup);
        } else {
            log.warn("No connection found for device {}, skipping reconnect", deviceId);
        }
    }

    public boolean isPending(String deviceId) {
        return pendingDevices.contains(deviceId);
    }

    public void shutdown() {
        pendingDevices.clear();
    }

    /**
     * Per-device exponential backoff tracker.
     * Each delay doubles until it reaches the configured maximum.
     */
    static class BackoffTracker {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final long initialDelayMs;
        private final long maxDelayMs;

        BackoffTracker(long initialDelayMs, long maxDelayMs) {
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        long nextDelay() {
            int attempt = attemptCount.getAndIncrement();
            long multiplier = attempt >= Long.SIZE - 1 ? Long.MAX_VALUE : 1L << attempt;
            long baseDelay = multiplier > maxDelayMs / initialDelayMs
                ? maxDelayMs
                : initialDelayMs * multiplier;
            long delay = Math.min(baseDelay, maxDelayMs);
            return addJitter(delay);
        }

        void reset() {
            attemptCount.set(0);
        }

        int getAttemptCount() {
            return attemptCount.get();
        }

        private long addJitter(long delay) {
            long jitter = (long) (ThreadLocalRandom.current().nextDouble(-0.1, 0.1) * delay);
            return Math.max(delay + jitter, initialDelayMs / 2);
        }
    }
}
