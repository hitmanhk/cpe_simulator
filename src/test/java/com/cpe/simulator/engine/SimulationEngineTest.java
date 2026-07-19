package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    @Test
    void testEngineConstruction() {
        AppConfig config = new AppConfig();
        config.setCount(1);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setMaxSessions(10);

        SimulationEngine engine = new SimulationEngine(config);
        assertNotNull(engine);
        assertFalse(engine.isRunning());
    }

    @Test
    void testStatusReportingBeforeStart() {
        AppConfig config = new AppConfig();
        config.setCount(1);
        config.setMaxSessions(10);

        SimulationEngine engine = new SimulationEngine(config);
        SimulationEngine.SimulationStatus status = engine.getStatus();

        assertNotNull(status);
        assertFalse(status.running());
        assertEquals(0, status.totalDevices());
        assertEquals(0, status.onlineDevices());
        assertEquals(0, status.activeSessions());
        assertEquals(0, status.totalInforms());
        assertEquals(Duration.ZERO, status.uptime());
        assertTrue(status.jvmMemoryUsed() > 0);
        assertTrue(status.jvmMemoryMax() > 0);
        assertTrue(status.threadCount() > 0);
    }

    @Test
    void testStatusRecordFields() {
        SimulationEngine.SimulationStatus status = new SimulationEngine.SimulationStatus(
            true, "public", 100, 95, 5, 500,
            Duration.ofMinutes(10), 512_000_000L, 2_000_000_000L, 50
        );

        assertTrue(status.running());
        assertEquals("public", status.mode());
        assertEquals(100, status.totalDevices());
        assertEquals(95, status.onlineDevices());
        assertEquals(5, status.activeSessions());
        assertEquals(500, status.totalInforms());
        assertEquals(Duration.ofMinutes(10), status.uptime());
        assertEquals(512_000_000L, status.jvmMemoryUsed());
        assertEquals(2_000_000_000L, status.jvmMemoryMax());
        assertEquals(50, status.threadCount());
    }

    @Test
    void testDeviceRegistryOperations() {
        DeviceRegistry registry = new DeviceRegistry();
        assertEquals(0, registry.getDeviceCount());

        AppConfig config = new AppConfig();
        config.setCount(1);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setMaxSessions(10);
        config.setSessionTimeout(30);

        com.cpe.simulator.model.DataModel dm = new com.cpe.simulator.model.DataModel();
        dm.saveDefaults();

        CpeDevice device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dm, config, null, null, null
        );

        registry.register(device.getDeviceId(), device);
        assertEquals(1, registry.getDeviceCount());
        assertTrue(registry.isRegistered("000000-SimCPE-SIM00001"));
        assertSame(device, registry.getDevice("000000-SimCPE-SIM00001"));

        registry.unregister("000000-SimCPE-SIM00001");
        assertEquals(0, registry.getDeviceCount());
        assertFalse(registry.isRegistered("000000-SimCPE-SIM00001"));
    }

    @Test
    void testResourceMonitor() {
        assertTrue(ResourceMonitor.getJvmMemoryUsed() > 0);
        assertTrue(ResourceMonitor.getJvmMemoryMax() > 0);
        assertTrue(ResourceMonitor.getThreadCount() > 0);
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
