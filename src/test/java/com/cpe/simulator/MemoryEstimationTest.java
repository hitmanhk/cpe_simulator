package com.cpe.simulator;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.DeviceManager;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryEstimationTest {

    @Test
    @Timeout(120)
    void testPerDeviceMemoryEstimation() throws Exception {
        AppConfig config = new AppConfig();
        config.setCount(1000);
        config.setMaxSessions(10);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:9999/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(findFreePort());
        config.setSessionTimeout(30);

        DataModel templateModel = ModelLoader.load("tr181");

        Runtime runtime = Runtime.getRuntime();

        System.gc();
        Thread.sleep(500);
        System.gc();
        Thread.sleep(500);

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        DeviceManager deviceManager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = deviceManager.createDevices(1000);

        assertEquals(1000, devices.size());

        assertNotNull(devices.get(0).getDeviceId());
        assertNotNull(devices.get(999).getDeviceId());

        System.gc();
        Thread.sleep(500);

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = memoryAfter - memoryBefore;
        if (memoryUsed < 0) {
            memoryUsed = 0;
        }

        double perDeviceBytes = (double) memoryUsed / 1000;
        double perDeviceKb = perDeviceBytes / 1024.0;

        System.out.printf("Memory estimation: total=%d bytes, per-device=%.1f KB (%.1f bytes)%n",
                memoryUsed, perDeviceKb, perDeviceBytes);

        double projectedFor50k = perDeviceKb * 50_000 / 1024.0;
        System.out.printf("Projected memory for 50K devices: %.1f MB%n", projectedFor50k);

        assertTrue(perDeviceKb < 60.0,
                String.format("Per-device memory should be < 60KB, was %.1f KB", perDeviceKb));

        deviceManager.shutdown();
    }

    @Test
    @Timeout(120)
    void testPerDeviceMemoryWithTr098Model() throws Exception {
        AppConfig config = new AppConfig();
        config.setCount(1000);
        config.setMaxSessions(10);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:9999/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(findFreePort());
        config.setSessionTimeout(30);

        DataModel templateModel = ModelLoader.load("tr098");

        Runtime runtime = Runtime.getRuntime();

        System.gc();
        Thread.sleep(500);
        System.gc();
        Thread.sleep(500);

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        DeviceManager deviceManager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = deviceManager.createDevices(1000);

        assertEquals(1000, devices.size());

        System.gc();
        Thread.sleep(500);

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = memoryAfter - memoryBefore;
        if (memoryUsed < 0) {
            memoryUsed = 0;
        }

        double perDeviceBytes = (double) memoryUsed / 1000;
        double perDeviceKb = perDeviceBytes / 1024.0;

        System.out.printf("TR-098 memory estimation: total=%d bytes, per-device=%.1f KB%n",
                memoryUsed, perDeviceKb);

        assertTrue(perDeviceKb < 60.0,
                String.format("Per-device memory should be < 60KB, was %.1f KB", perDeviceKb));

        deviceManager.shutdown();
    }

    @Test
    @Timeout(60)
    void testDataModelDeepCopyIndependence() {
        DataModel original = ModelLoader.load("tr181");
        DataModel copy1 = original.deepCopy();
        DataModel copy2 = original.deepCopy();

        assertNotSame(copy1, copy2);
        assertNotSame(copy1.getRoot(), copy2.getRoot());

        assertNotNull(copy1.getRoot());
        assertNotNull(copy2.getRoot());
    }

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
