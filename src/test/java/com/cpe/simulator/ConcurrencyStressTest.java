package com.cpe.simulator;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.DeviceManager;
import com.cpe.simulator.model.DataModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyStressTest {

    @Test
    @Timeout(60)
    void testSemaphoreLimitsConcurrentSessions() throws Exception {
        int deviceCount = 100;
        int maxSessions = 10;

        Semaphore sessionSemaphore = new Semaphore(maxSessions);
        AtomicInteger concurrentSessions = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrent = new AtomicInteger(0);
        AtomicInteger completedSessions = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(deviceCount);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < deviceCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    boolean acquired = sessionSemaphore.tryAcquire(30, TimeUnit.SECONDS);
                    if (!acquired) {
                        return;
                    }

                    try {
                        int current = concurrentSessions.incrementAndGet();
                        maxObservedConcurrent.updateAndGet(max -> Math.max(max, current));

                        Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                    } finally {
                        concurrentSessions.decrementAndGet();
                        sessionSemaphore.release();
                        completedSessions.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(50, TimeUnit.SECONDS), "All sessions should complete");

        assertEquals(deviceCount, completedSessions.get(), "All sessions should complete");
        assertTrue(maxObservedConcurrent.get() <= maxSessions,
                "Max concurrent sessions should be <= " + maxSessions + ", was: " + maxObservedConcurrent.get());
        assertTrue(maxObservedConcurrent.get() > 1,
                "Should have had some concurrent sessions, max was: " + maxObservedConcurrent.get());

        executor.close();
    }

    @Test
    @Timeout(60)
    void testDeviceManagerCreatesCorrectCount() throws Exception {
        AppConfig config = new AppConfig();
        config.setCount(100);
        config.setMaxSessions(10);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:9999/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(findFreePort());
        config.setSessionTimeout(30);

        DataModel templateModel = new DataModel();
        templateModel.saveDefaults();

        DeviceManager deviceManager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = deviceManager.createDevices(100);

        assertEquals(100, devices.size());
        assertEquals(100, deviceManager.getDeviceCount());

        long uniqueIds = devices.stream()
                .map(CpeDevice::getDeviceId)
                .distinct()
                .count();
        assertEquals(100, uniqueIds, "All device IDs should be unique");

        deviceManager.shutdown();
    }

    @Test
    @Timeout(60)
    void testSemaphoreBlocksExcessSessions() throws Exception {
        int maxSessions = 3;
        Semaphore semaphore = new Semaphore(maxSessions);
        AtomicInteger activeCount = new AtomicInteger(0);
        AtomicInteger maxActive = new AtomicInteger(0);
        CountDownLatch allStarted = new CountDownLatch(10);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(10);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    boolean acquired = semaphore.tryAcquire(5, TimeUnit.SECONDS);
                    if (!acquired) {
                        return;
                    }
                    try {
                        int current = activeCount.incrementAndGet();
                        maxActive.updateAndGet(max -> Math.max(max, current));
                        allStarted.countDown();
                        releaseLatch.await(10, TimeUnit.SECONDS);
                    } finally {
                        activeCount.decrementAndGet();
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        Thread.sleep(500);
        assertTrue(activeCount.get() <= maxSessions,
                "Active count should not exceed max sessions");

        releaseLatch.countDown();
        assertTrue(allDone.await(15, TimeUnit.SECONDS));

        assertTrue(maxActive.get() <= maxSessions,
                "Max active should be <= " + maxSessions + ", was: " + maxActive.get());

        executor.close();
    }

    @Test
    @Timeout(60)
    void testHighConcurrencyWithDeviceManager() throws Exception {
        int deviceCount = 100;
        int maxSessions = 10;

        AppConfig config = new AppConfig();
        config.setCount(deviceCount);
        config.setMaxSessions(maxSessions);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:9999/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(findFreePort());
        config.setSessionTimeout(30);

        DataModel templateModel = new DataModel();
        templateModel.saveDefaults();

        DeviceManager deviceManager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = deviceManager.createDevices(deviceCount);
        Semaphore semaphore = deviceManager.getSessionSemaphore();

        AtomicInteger concurrentSessions = new AtomicInteger(0);
        AtomicInteger maxObserved = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(deviceCount);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (CpeDevice device : devices) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
                    if (!acquired) return;

                    try {
                        int current = concurrentSessions.incrementAndGet();
                        maxObserved.updateAndGet(max -> Math.max(max, current));
                        Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
                    } finally {
                        concurrentSessions.decrementAndGet();
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(50, TimeUnit.SECONDS));

        assertTrue(maxObserved.get() <= maxSessions,
                "Max concurrent should be <= " + maxSessions + ", was: " + maxObserved.get());

        executor.close();
        deviceManager.shutdown();
    }

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
