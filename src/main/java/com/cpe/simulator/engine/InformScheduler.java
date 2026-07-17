package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;

import java.util.List;
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

    InformScheduler(AppConfig config, ScheduledExecutorService scheduler) {
        this.config = config;
        this.scheduler = scheduler;
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    public void schedulePeriodicInform(CpeDevice device) {
        scheduleInform(device, "2 PERIODIC", config.getInformSchedules().getPeriodic());
    }

    public void scheduleConfiguredInforms(CpeDevice device) {
        AppConfig.InformSchedules schedules = config.getInformSchedules();
        scheduleInform(device, "0 BOOTSTRAP", schedules.getBootstrap());
        scheduleInform(device, "1 BOOT", schedules.getBoot());
        scheduleInform(device, "2 PERIODIC", schedules.getPeriodic());
    }

    private void scheduleInform(CpeDevice device, String eventCode, AppConfig.InformSchedule schedule) {
        if (schedule.getInterval() == 0 || scheduler.isShutdown()) {
            return;
        }

        int interval = schedule.getInterval();
        double initialJitter = schedule.getInitialJitter();

        Runnable task = () -> {
            try {
                device.triggerInform(eventCode);
            } catch (Exception e) {
                // ignore individual inform failures
            }
        };

        long initialDelay = computeJitteredInterval(interval, initialJitter);
        long period = interval;

        try {
            scheduledTasks.computeIfAbsent(taskKey(device.getDeviceId(), eventCode), key ->
                scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS)
            );
        } catch (RejectedExecutionException ignored) {
            // Engine shutdown raced with an XMPP ONLINE notification.
        }
    }

    public void cancelScheduledInform(String deviceId) {
        String prefix = deviceId + "|";
        for (String key : List.copyOf(scheduledTasks.keySet())) {
            if (key.startsWith(prefix)) {
                ScheduledFuture<?> future = scheduledTasks.remove(key);
                if (future != null) {
                    future.cancel(false);
                }
            }
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

    long computeJitteredInterval(int interval, double jitter) {
        double factor = 1.0 + (Math.random() * 2 - 1) * jitter;
        return Math.max(1, (long) (interval * factor));
    }

    int getScheduledTaskCount() {
        return scheduledTasks.size();
    }

    private String taskKey(String deviceId, String eventCode) {
        return deviceId + "|" + eventCode;
    }
}
