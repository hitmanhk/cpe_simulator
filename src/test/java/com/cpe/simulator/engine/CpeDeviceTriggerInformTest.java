package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpSession;
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
class CpeDeviceTriggerInformTest {

    private CpeDevice device;
    private DataModel dataModel;
    private AppConfig config;

    @Mock
    private ScheduledExecutorService mockScheduler;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.setCount(1);
        config.setSessionTimeout(30);
        config.setMaxSessions(10);

        dataModel = new DataModel();
        dataModel.saveDefaults();

        Semaphore semaphore = new Semaphore(10);

        device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dataModel, config, null, mockScheduler, semaphore
        );
    }

    @Test
    void triggerInformRejectsOfflineDevice() {
        device.setState(CpeDevice.DeviceState.OFFLINE);
        device.triggerInform("6 CONNECTION REQUEST");

        verify(mockScheduler, never()).execute(any());
        assertEquals(CwmpSession.State.IDLE, device.getSession().getState());
        assertFalse(device.isSessionActive());
    }

    @Test
    void triggerInformAcceptsOnlineDevice() {
        device.setState(CpeDevice.DeviceState.ONLINE);
        device.triggerInform("6 CONNECTION REQUEST");

        verify(mockScheduler, times(1)).execute(any());
        assertTrue(device.isSessionActive());
    }

    @Test
    void triggerInformAcceptsCreatedDevice() {
        device.setState(CpeDevice.DeviceState.CREATED);
        device.triggerInform("1 BOOT");

        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void triggerInformAcceptsConnectingDevice() {
        device.setState(CpeDevice.DeviceState.CONNECTING);
        device.triggerInform("1 BOOT");

        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void casSuccessTriggersInform() {
        device.setState(CpeDevice.DeviceState.ONLINE);
        assertFalse(device.isSessionActive());

        device.triggerInform("6 CONNECTION REQUEST");

        assertTrue(device.isSessionActive());
        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void casFailureSkipsInform() {
        device.setState(CpeDevice.DeviceState.ONLINE);
        device.triggerInform("6 CONNECTION REQUEST");

        assertTrue(device.isSessionActive());
        verify(mockScheduler, times(1)).execute(any());

        device.triggerInform("2 PERIODIC");
        device.triggerInform("6 CONNECTION REQUEST");

        assertTrue(device.isSessionActive());
        verify(mockScheduler, times(1)).execute(any());
    }

    @Test
    void sessionActiveReleasedAfterRunCompletes() {
        Semaphore semaphore = new Semaphore(10);
        device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dataModel, config, null, mockScheduler, semaphore
        );
        device.setState(CpeDevice.DeviceState.ONLINE);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockScheduler).execute(any());

        assertFalse(device.isSessionActive());
        device.triggerInform("6 CONNECTION REQUEST");
        assertFalse(device.isSessionActive());
    }

    @Test
    void sessionActiveSetBeforeSchedulerSubmission() {
        Semaphore semaphore = new Semaphore(0);
        device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dataModel, config, null, mockScheduler, semaphore
        );
        device.setState(CpeDevice.DeviceState.ONLINE);

        assertFalse(device.isSessionActive());
        device.triggerInform("6 CONNECTION REQUEST");
        assertTrue(device.isSessionActive());

        verify(mockScheduler, times(1)).execute(any());
    }
}