package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.model.DataModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class XmppStateBridgeTest {

    private AppConfig config;
    private CpeDevice device;
    private XmppConnection xmppConnection;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.setCount(1);
        config.setSessionTimeout(30);
        config.setMaxSessions(10);

        DataModel dm = new DataModel();
        dm.saveDefaults();

        scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        Semaphore semaphore = new Semaphore(10);

        device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dm, config, null, scheduler, semaphore
        );
        device.initialize();

        xmppConnection = new XmppConnection(device.getDeviceId(), config.getXmpp(), null, null);
        device.setXmppConnection(xmppConnection);
    }

    @Test
    void testOnlineStateSyncsToDevice() {
        xmppConnection.onStateChange(xmppState -> {
            switch (xmppState) {
                case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                case FAILED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                default -> {}
            }
        });

        assertEquals(CpeDevice.DeviceState.CREATED, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.ONLINE);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
    }

    @Test
    void testFailedStateSyncsToDeviceOffline() {
        xmppConnection.onStateChange(xmppState -> {
            switch (xmppState) {
                case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                case FAILED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                default -> {}
            }
        });

        xmppConnection.setState(XmppConnection.ConnectionState.FAILED);
        assertEquals(CpeDevice.DeviceState.OFFLINE, device.getState());
    }

    @Test
    void testDisconnectedStateSyncsToDeviceOffline() {
        xmppConnection.onStateChange(xmppState -> {
            switch (xmppState) {
                case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                case FAILED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                default -> {}
            }
        });

        xmppConnection.setState(XmppConnection.ConnectionState.ONLINE);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.DISCONNECTED);
        assertEquals(CpeDevice.DeviceState.OFFLINE, device.getState());
    }

    @Test
    void testIntermediateStatesIgnored() {
        xmppConnection.onStateChange(xmppState -> {
            switch (xmppState) {
                case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                case FAILED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                default -> {}
            }
        });

        device.setState(CpeDevice.DeviceState.ONLINE);

        xmppConnection.setState(XmppConnection.ConnectionState.CONNECTING);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.AUTHENTICATING);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.BINDING);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
    }

    @Test
    void testReconnectSequence() {
        xmppConnection.onStateChange(xmppState -> {
            switch (xmppState) {
                case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                case FAILED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                default -> {}
            }
        });

        xmppConnection.setState(XmppConnection.ConnectionState.ONLINE);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.DISCONNECTED);
        assertEquals(CpeDevice.DeviceState.OFFLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.CONNECTING);
        assertEquals(CpeDevice.DeviceState.OFFLINE, device.getState());

        xmppConnection.setState(XmppConnection.ConnectionState.ONLINE);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
