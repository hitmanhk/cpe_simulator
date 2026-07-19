package com.cpe.simulator.desktop.bridge;

import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterValue;

import java.time.Instant;

/**
 * Immutable point-in-time copy of a device's UI-relevant state. The desktop
 * UI renders snapshots instead of reading live engine objects, so table
 * updates never contend with engine threads.
 */
public record DeviceSnapshot(String deviceId, String serial, String oui, String productClass,
                             String vendorName, String state, String connectionType,
                             Instant onlineSince, long informCount, Instant lastInformTime,
                             boolean sessionActive, String softwareVersion, String hardwareVersion,
                             String connReqHost, String acsHost) {

    public static DeviceSnapshot of(CpeDevice device) {
        DataModel model = device.getDataModel();
        return new DeviceSnapshot(
                device.getDeviceId(),
                device.getSerialNumber(),
                device.getOui(),
                device.getProductClass(),
                device.getVendorName(),
                device.getState().name(),
                device.getConnectionType(),
                device.getOnlineSince(),
                device.getInformCount(),
                device.getLastInformTime(),
                device.isSessionActive(),
                readRelative(model, "DeviceInfo.SoftwareVersion"),
                readRelative(model, "DeviceInfo.HardwareVersion"),
                hostOf(readRelative(model, "ManagementServer.ConnectionRequestURL")),
                hostOf(readRelative(model, "ManagementServer.URL")));
    }

    private static String readRelative(DataModel model, String relativePath) {
        try {
            ParameterValue value = model.getRelativeValue(relativePath);
            return value != null ? value.getValue() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 URL 提取 host 部分（http://host:port/path → host）。 */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            return java.net.URI.create(url).getHost() != null
                    ? java.net.URI.create(url).getHost() : url;
        } catch (Exception e) {
            return url;
        }
    }
}
