package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.SimLogger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BootLauncher {

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;

    public BootLauncher(AppConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Thread.ofVirtual().factory()
        );
    }

    public CompletableFuture<Void> launchBoot(List<CpeDevice> devices) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        int batchSize = config.getBootBatchSize();
        int batchDelay = config.getBootBatchDelay();
        double jitter = config.getBootJitter();
        long jitterMaxMs = (long) (batchDelay * jitter);

        scheduler.execute(() -> {
            try {
                for (int i = 0; i < devices.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, devices.size());

                    for (int j = i; j < end; j++) {
                        CpeDevice device = devices.get(j);
                        long deviceDelay = jitterMaxMs > 0
                            ? (long) (Math.random() * jitterMaxMs)
                            : 0;

                        if (deviceDelay > 0) {
                            scheduler.schedule(() -> {
                                try {
                                    device.triggerInform("1 BOOT");
                                } catch (Exception e) {
                                    SimLogger.error("BOOT inform failed for device " + device.getDeviceId(), e);
                                }
                            }, deviceDelay, TimeUnit.MILLISECONDS);
                        } else {
                            try {
                                device.triggerInform("1 BOOT");
                            } catch (Exception e) {
                                SimLogger.error("BOOT inform failed for device " + device.getDeviceId(), e);
                            }
                        }
                    }

                    if (end < devices.size()) {
                        Thread.sleep(batchDelay);
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}