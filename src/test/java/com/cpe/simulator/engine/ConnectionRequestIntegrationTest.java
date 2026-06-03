package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpClient;
import com.cpe.simulator.model.DataModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionRequestIntegrationTest {

    private DeviceRegistry registry;
    private AppConfig config;

    @Mock
    private CwmpClient mockCwmpClient;

    @Mock
    private ScheduledExecutorService mockScheduler;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.setCount(1);
        config.setSessionTimeout(30);
        config.setMaxSessions(10);

        registry = new DeviceRegistry();
    }

    private CpeDevice createOnlineDevice(String deviceId) {
        DataModel dataModel = new DataModel();
        dataModel.saveDefaults();

        Semaphore semaphore = new Semaphore(10);
        CpeDevice device = new CpeDevice(
            deviceId, "SIM00001", "000000", "SimCPE",
            dataModel, config, mockCwmpClient, mockScheduler, semaphore
        );
        device.setState(CpeDevice.DeviceState.ONLINE);
        registry.register(deviceId, device);
        return device;
    }

    @Test
    void handleXmppConnectionRequestTriggersInformWhenIdle() {
        CpeDevice device = createOnlineDevice("device-001");
        assertFalse(device.isSessionActive());

        ConnectionRequestIntegration integration = new ConnectionRequestIntegration(config, registry);
        integration.handleXmppConnectionRequest("device-001");

        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void handleXmppConnectionRequestSkipsInformWhenSessionActive() {
        CpeDevice device = createOnlineDevice("device-001");
        device.triggerInform("6 CONNECTION REQUEST");
        assertTrue(device.isSessionActive());

        ConnectionRequestIntegration integration = new ConnectionRequestIntegration(config, registry);
        integration.handleXmppConnectionRequest("device-001");

        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void handleXmppConnectionRequestSkipsUnknownDevice() {
        ConnectionRequestIntegration integration = new ConnectionRequestIntegration(config, registry);
        integration.handleXmppConnectionRequest("nonexistent");

        verify(mockScheduler, never()).execute(any());
    }
}