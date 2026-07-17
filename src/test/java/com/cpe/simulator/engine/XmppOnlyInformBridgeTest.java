package com.cpe.simulator.engine;

import com.cpe.simulator.xmpp.XmppConnection;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class XmppOnlyInformBridgeTest {

    @Test
    void onlineStartsScheduledInformsForDevice() {
        CpeDevice device = mock(CpeDevice.class);
        InformScheduler scheduler = mock(InformScheduler.class);
        XmppOnlyInformBridge bridge = new XmppOnlyInformBridge(device, scheduler);

        bridge.onStateChange(XmppConnection.ConnectionState.ONLINE);

        verify(device).setState(CpeDevice.DeviceState.ONLINE);
        verify(scheduler).scheduleConfiguredInforms(device);
    }

    @Test
    void disconnectStopsScheduledInformsForDevice() {
        CpeDevice device = mock(CpeDevice.class);
        when(device.getDeviceId()).thenReturn("device-1");
        InformScheduler scheduler = mock(InformScheduler.class);
        XmppOnlyInformBridge bridge = new XmppOnlyInformBridge(device, scheduler);

        bridge.onStateChange(XmppConnection.ConnectionState.DISCONNECTED);

        verify(device).setState(CpeDevice.DeviceState.OFFLINE);
        verify(scheduler).cancelScheduledInform("device-1");
    }

    @Test
    void intermediateStatesDoNotStartScheduledInforms() {
        CpeDevice device = mock(CpeDevice.class);
        InformScheduler scheduler = mock(InformScheduler.class);
        XmppOnlyInformBridge bridge = new XmppOnlyInformBridge(device, scheduler);

        bridge.onStateChange(XmppConnection.ConnectionState.AUTHENTICATING);

        verifyNoInteractions(device, scheduler);
    }
}
