package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;

import java.util.Map;
import java.util.concurrent.*;

public class InformScheduler {

    private final ScheduledExecutorService scheduler;
    private final AppConfig config;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public InformScheduler(AppConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Thread.ofVirtual().factory()
        );
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    public void schedulePeriodicInform(CpeDevice device) {
        int interval = config.getInformInterval();
        double jitter = config.getInformJitter();

        Runnable task = () -> {
            try {
                device.triggerInform("2 PERIODIC");
            } catch (Exception e) {
                // ignore individual inform failures
            }
        };

        long initialDelay = computeJitteredInterval(interval, jitter);
        long period = interval;

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            task, initialDelay, period, TimeUnit.SECONDS
        );
        scheduledTasks.put(device.getDeviceId(), future);
    }

    public void cancelScheduledInform(String deviceId) {
        ScheduledFuture<?> future = scheduledTasks.remove(deviceId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void triggerAllInforms(Iterable<CpeDevice> devices) {
        for (CpeDevice device : devices) {
            device.triggerInform("2 PERIODIC");
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private long computeJitteredInterval(int interval, double jitter) {
        double factor = 1.0 + (Math.random() * 2 - 1) * jitter;
        return Math.max(1, (long) (interval * factor));
    }
}
