package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.cwmp.DeviceContext;

public class ConnectionRequestIntegration {

    private final DeviceRegistry registry;
    private final CwmpServer cwmpServer;
    private final AppConfig config;

    public ConnectionRequestIntegration(AppConfig config, DeviceRegistry registry) {
        this.config = config;
        this.registry = registry;
        this.cwmpServer = new CwmpServer(config, registry);
    }

    public void start() throws InterruptedException {
        cwmpServer.start();
    }

    public void stop() {
        cwmpServer.stop();
    }

    public void configureConnectionRequestUrl(DeviceContext device) {
        String deviceId = device.getDeviceId();
        String host = config.getConnReqHost();
        int port = config.getConnReqPort();
        String url = String.format("http://%s:%d/connReq/%s", host, port, deviceId);

        String paramPath;
        if ("tr098".equalsIgnoreCase(config.getModel())) {
            paramPath = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";
        } else {
            paramPath = "Device.ManagementServer.ConnectionRequestURL";
        }

        try {
            device.getDataModel().setValue(paramPath, url);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to set ConnectionRequestURL for device " + deviceId, e);
        }
    }

    public void handleXmppConnectionRequest(String deviceId) {
        DeviceContext device = registry.getDevice(deviceId);
        if (device != null) {
            device.triggerInform("6 CONNECTION REQUEST");
        }
    }

    public CwmpServer getServer() {
        return cwmpServer;
    }
}
