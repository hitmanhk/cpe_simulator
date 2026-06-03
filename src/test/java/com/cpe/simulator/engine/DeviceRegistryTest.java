package com.cpe.simulator.engine;

import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.cwmp.CwmpSession;
import com.cpe.simulator.cwmp.DeviceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryTest {

    private DeviceRegistry registry;

    @Mock
    private DeviceContext mockDevice1;

    @Mock
    private DeviceContext mockDevice2;

    @Mock
    private CwmpSession mockSession1;

    @Mock
    private CwmpSession mockSession2;

    @BeforeEach
    void setUp() {
        registry = new DeviceRegistry();
    }

    @Test
    void registerAndGetDevice() {
        registry.register("device-001", mockDevice1);

        assertTrue(registry.isRegistered("device-001"));
        assertEquals(mockDevice1, registry.getDevice("device-001"));
        assertEquals(1, registry.getDeviceCount());
    }

    @Test
    void unregisterDevice() {
        registry.register("device-001", mockDevice1);
        registry.unregister("device-001");

        assertFalse(registry.isRegistered("device-001"));
        assertNull(registry.getDevice("device-001"));
        assertEquals(0, registry.getDeviceCount());
    }

    @Test
    void getDeviceReturnsNullForUnregistered() {
        assertNull(registry.getDevice("nonexistent"));
        assertFalse(registry.isRegistered("nonexistent"));
    }

    @Test
    void getAllDevicesReturnsAllRegistered() {
        registry.register("device-001", mockDevice1);
        registry.register("device-002", mockDevice2);

        assertEquals(2, registry.getAllDevices().size());
        assertTrue(registry.getAllDevices().contains(mockDevice1));
        assertTrue(registry.getAllDevices().contains(mockDevice2));
    }

    @Test
    void handleConnectionRequestWithRegisteredDevice() {
        when(mockDevice1.getSession()).thenReturn(mockSession1);
        when(mockSession1.getState()).thenReturn(CwmpSession.State.IDLE);

        registry.register("device-001", mockDevice1);

        CwmpServer.ConnectionRequestResult result = registry.handleConnectionRequest("device-001");

        assertTrue(result.success());
        assertEquals(200, result.statusCode());
        verify(mockDevice1).triggerInform("6 CONNECTION REQUEST");
    }

    @Test
    void handleConnectionRequestWithUnregisteredDevice() {
        CwmpServer.ConnectionRequestResult result = registry.handleConnectionRequest("nonexistent");

        assertFalse(result.success());
        assertEquals(404, result.statusCode());
    }

    @Test
    void handleConnectionRequestWithBusyDevice() {
        when(mockDevice1.getSession()).thenReturn(mockSession1);
        when(mockSession1.getState()).thenReturn(CwmpSession.State.IN_SESSION);

        registry.register("device-001", mockDevice1);

        CwmpServer.ConnectionRequestResult result = registry.handleConnectionRequest("device-001");

        assertFalse(result.success());
        assertEquals(503, result.statusCode());
        verify(mockDevice1, never()).triggerInform(anyString());
    }

    @Test
    void getOnlineCountCountsActiveSessions() {
        when(mockDevice1.getSession()).thenReturn(mockSession1);
        when(mockSession1.getState()).thenReturn(CwmpSession.State.IN_SESSION);

        when(mockDevice2.getSession()).thenReturn(mockSession2);
        when(mockSession2.getState()).thenReturn(CwmpSession.State.IDLE);

        registry.register("device-001", mockDevice1);
        registry.register("device-002", mockDevice2);

        assertEquals(1, registry.getOnlineCount());
    }

    @Test
    void concurrentRegisterAndUnregister() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String deviceId = "device-" + threadId + "-" + i;
                        DeviceContext mockDevice = mock(DeviceContext.class);
                        registry.register(deviceId, mockDevice);
                        registry.isRegistered(deviceId);
                        registry.getDeviceCount();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * operationsPerThread, registry.getDeviceCount());
    }

    @Test
    void registerOverwritesExistingDevice() {
        registry.register("device-001", mockDevice1);
        registry.register("device-001", mockDevice2);

        assertEquals(mockDevice2, registry.getDevice("device-001"));
        assertEquals(1, registry.getDeviceCount());
    }
}
