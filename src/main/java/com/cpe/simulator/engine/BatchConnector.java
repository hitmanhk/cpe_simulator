package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BatchConnector {

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;

    public BatchConnector(AppConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    }

    public CompletableFuture<Void> connectBatch(List<CpeDevice> devices) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        int batchSize = config.getConnectBatch();
        int batchDelay = config.getConnectBatchDelay();

        scheduler.execute(() -> {
            try {
                for (int i = 0; i < devices.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, devices.size());
                    for (int j = i; j < end; j++) {
                        devices.get(j).connect();
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