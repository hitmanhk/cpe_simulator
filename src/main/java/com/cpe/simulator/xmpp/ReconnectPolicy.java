package com.cpe.simulator.xmpp;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReconnectPolicy {

    private final long initialDelay;
    private final long maxDelay;
    private long currentDelay;
    private final Random random;

    public ReconnectPolicy() {
        this(1000L, 60000L);
    }

    public ReconnectPolicy(long initialDelay, long maxDelay) {
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.currentDelay = initialDelay;
        this.random = new Random();
    }

    public long nextDelay() {
        long jitter = (long) (random.nextDouble() * 0.25 * currentDelay);
        long delay = currentDelay + jitter;
        currentDelay = Math.min(currentDelay * 2, maxDelay);
        return delay;
    }

    public void reset() {
        currentDelay = initialDelay;
    }

    public ScheduledFuture<?> scheduleReconnect(Runnable reconnectAction, ScheduledExecutorService scheduler) {
        long delay = nextDelay();
        return scheduler.schedule(reconnectAction, delay, TimeUnit.MILLISECONDS);
    }

    public long getCurrentDelay() {
        return currentDelay;
    }
}
