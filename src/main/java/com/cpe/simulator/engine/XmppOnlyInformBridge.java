package com.cpe.simulator.engine;

import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.xmpp.XmppConnection;

class XmppOnlyInformBridge {

    private final CpeDevice device;
    private final InformScheduler informScheduler;

    XmppOnlyInformBridge(CpeDevice device, InformScheduler informScheduler) {
        this.device = device;
        this.informScheduler = informScheduler;
    }

    void onStateChange(XmppConnection.ConnectionState xmppState) {
        switch (xmppState) {
            case CONNECTING, TLS_NEGOTIATING, AUTHENTICATING, AUTHENTICATED, BINDING, BOUND ->
                device.transitionState(CpeDevice.DeviceState.CONNECTING, "XMPP state=" + xmppState);
            case ONLINE -> {
                device.transitionState(CpeDevice.DeviceState.ONLINE, "XMPP connected");
                informScheduler.scheduleConfiguredInforms(device);
            }
            case FAILED -> {
                device.transitionState(CpeDevice.DeviceState.OFFLINE, "XMPP connection failed");
                informScheduler.cancelScheduledInform(device.getDeviceId());
                SimLogger.error("XMPP connection failed for device {}", device.getDeviceId());
            }
            case DISCONNECTED -> {
                device.transitionState(CpeDevice.DeviceState.OFFLINE, "XMPP disconnected");
                informScheduler.cancelScheduledInform(device.getDeviceId());
            }
        }
    }
}
