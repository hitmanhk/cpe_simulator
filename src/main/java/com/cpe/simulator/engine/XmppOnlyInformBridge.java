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
            case ONLINE -> {
                device.setState(CpeDevice.DeviceState.ONLINE);
                informScheduler.scheduleConfiguredInforms(device);
            }
            case FAILED -> {
                device.setState(CpeDevice.DeviceState.OFFLINE);
                informScheduler.cancelScheduledInform(device.getDeviceId());
                SimLogger.error("XMPP connection failed for device {}", device.getDeviceId());
            }
            case DISCONNECTED -> {
                device.setState(CpeDevice.DeviceState.OFFLINE);
                informScheduler.cancelScheduledInform(device.getDeviceId());
            }
            default -> {
            }
        }
    }
}
