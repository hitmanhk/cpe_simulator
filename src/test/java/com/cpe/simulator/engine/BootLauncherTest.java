package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BootLauncherTest {

    @Test
    void testLaunchBootCompletesForSmallBatch() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(10);
        config.setBootBatchDelay(100);
        config.setBootJitter(0.0);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(20);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testLaunchBootWithJitterCompletes() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(10);
        config.setBootBatchDelay(200);
        config.setBootJitter(0.5);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(30);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testSingleBatchNoDelay() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(100);
        config.setBootBatchDelay(1000);
        config.setBootJitter(0.0);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(50);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testDefaultConfigValues() {
        AppConfig config = new AppConfig();
        assertEquals(100, config.getBootBatchSize());
        assertEquals(1000, config.getBootBatchDelay());
        assertEquals(0.5, config.getBootJitter());
    }

    @Test
    void normalStartupTriggersBootstrapEvent() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(1);
        config.setBootBatchDelay(0);
        config.setBootJitter(0.0);
        CpeDevice device = mock(CpeDevice.class);

        BootLauncher launcher = new BootLauncher(config);
        try {
            launcher.launchBoot(List.of(device)).join();
            verify(device).triggerInform("0 BOOTSTRAP");
        } finally {
            launcher.shutdown();
        }
    }

    private List<CpeDevice> createMockDevices(int count) {
        List<CpeDevice> devices = new ArrayList<>();
        com.cpe.simulator.model.DataModel dm = new com.cpe.simulator.model.DataModel();
        dm.saveDefaults();

        Semaphore semaphore = new Semaphore(100);
        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        AppConfig config = new AppConfig();
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:7547/");

        for (int i = 0; i < count; i++) {
            String sn = "SIM" + String.format("%05d", i + 1);
            String deviceId = "000000-SimCPE-" + sn;
            com.cpe.simulator.model.DataModel deviceModel = dm.deepCopy();

            CpeDevice device = new CpeDevice(
                deviceId, sn, "000000", "SimCPE",
                deviceModel, config, null, scheduler, semaphore
            );
            device.initialize();
            device.connect();
            devices.add(device);
        }

        return devices;
    }
}
