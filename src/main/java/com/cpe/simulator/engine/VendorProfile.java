package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;

/**
 * Vendor (manufacturer) profile grouping a set of simulated devices.
 * The OUI and ProductClass feed into each device's TR-069 DeviceId and the
 * {@code ${device.oui}} / {@code ${device.productClass}} template variables;
 * the connection-request credentials are used for per-vendor connection
 * request authentication (HTTP and XMPP paths), falling back to the global
 * config credentials when absent.
 */
public record VendorProfile(String name, String manufacturer, String oui,
                            String productClass, String connReqUser, String connReqPassword) {

    public static final String DEFAULT_VENDOR_NAME = "默认厂商";

    public VendorProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("厂商名称不能为空");
        }
        if (oui == null || oui.isBlank()) {
            throw new IllegalArgumentException("OUI 不能为空");
        }
        if (productClass == null || productClass.isBlank()) {
            throw new IllegalArgumentException("ProductClass 不能为空");
        }
    }

    /** 厂商是否配置了专属回连凭据。 */
    public boolean hasConnReqAuth() {
        return connReqUser != null && !connReqUser.isEmpty()
                && connReqPassword != null && !connReqPassword.isEmpty();
    }

    /** The built-in default vendor, derived from the app config. */
    public static VendorProfile defaultVendor(AppConfig config) {
        return new VendorProfile(DEFAULT_VENDOR_NAME, "SimVendor",
                config.getOui(), config.getProductClass(),
                config.getConnReqUser(), config.getConnReqPassword());
    }
}
