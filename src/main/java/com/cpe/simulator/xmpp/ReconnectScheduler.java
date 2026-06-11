package com.cpe.simulator.xmpp;

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

    static final long INITIAL_DELAY_MS = 30_000;    // 30 seconds
    static final long MAX_DELAY_MS = 30 * 60_000;   // 30 minutes

    private final Set<String> pendingDevices = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, BackoffTracker> backoffTrackers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Function<String, XmppConnection> connectionProvider;
    private final EventLoopGroup eventLoopGroup;

    public ReconnectScheduler(ScheduledExecutorService scheduler,
                               Function<String, XmppConnection> connectionProvider,
                               EventLoopGroup eventLoopGroup) {
        this.scheduler = scheduler;
        this.connectionProvider = connectionProvider;
        this.eventLoopGroup = eventLoopGroup;
    }

    public void onFailure(String deviceId) {
        if (!pendingDevices.add(deviceId)) {
            log.debug("Reconnect already pending for device {}, ignoring duplicate", deviceId);
            return;
        }
        BackoffTracker tracker = backoffTrackers.computeIfAbsent(deviceId, k -> new BackoffTracker());
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
        scheduler.shutdownNow();
        pendingDevices.clear();
    }

    /**
     * Per-device exponential backoff tracker.
     * Delay sequence: 30s, 1min, 2min, 4min, 8min, 16min, 30min, 30min, ...
     */
    static class BackoffTracker {
        private final AtomicInteger attemptCount = new AtomicInteger(0);

        long nextDelay() {
            int attempt = attemptCount.getAndIncrement();
            long baseDelay = INITIAL_DELAY_MS * (1L << attempt);
            long delay = Math.min(baseDelay, MAX_DELAY_MS);
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
            return Math.max(delay + jitter, INITIAL_DELAY_MS / 2);
        }
    }
}
