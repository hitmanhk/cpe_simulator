package com.cpe.simulator.engine;

import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.cwmp.CwmpSession;
import com.cpe.simulator.cwmp.DeviceContext;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceRegistry implements CwmpServer.ConnectionRequestHandler {

    private final ConcurrentHashMap<String, DeviceContext> devices = new ConcurrentHashMap<>();

    public void register(String deviceId, DeviceContext device) {
        devices.put(deviceId, device);
    }

    public void unregister(String deviceId) {
        devices.remove(deviceId);
    }

    public DeviceContext getDevice(String deviceId) {
        return devices.get(deviceId);
    }

    public Collection<DeviceContext> getAllDevices() {
        return devices.values();
    }

    public int getDeviceCount() {
        return devices.size();
    }

    public int getOnlineCount() {
        int count = 0;
        for (DeviceContext device : devices.values()) {
            CwmpSession session = device.getSession();
            if (session != null && session.getState() != CwmpSession.State.IDLE) {
                count++;
            }
        }
        return count;
    }

    public boolean isRegistered(String deviceId) {
        return devices.containsKey(deviceId);
    }

    @Override
    public CwmpServer.ConnectionRequestResult handleConnectionRequest(String deviceId) {
        DeviceContext device = devices.get(deviceId);
        if (device == null) {
            return new CwmpServer.ConnectionRequestResult(false, 404, "Device not found");
        }

        if (device instanceof CpeDevice cpeDevice && cpeDevice.isSessionActive()) {
            return new CwmpServer.ConnectionRequestResult(true, 200, "OK");
        }

        try {
            device.triggerInform("6 CONNECTION REQUEST");
            return new CwmpServer.ConnectionRequestResult(true, 200, "Connection request accepted");
        } catch (Exception e) {
            return new CwmpServer.ConnectionRequestResult(false, 503, "Device offline: " + e.getMessage());
        }
    }
}
