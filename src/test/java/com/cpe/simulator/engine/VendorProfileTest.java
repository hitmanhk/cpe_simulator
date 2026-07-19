package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VendorProfileTest {

    @Test
    void defaultVendorDerivesFromConfig() {
        AppConfig config = new AppConfig();
        config.setOui("A1B2C3");
        config.setProductClass("Router");
        config.setConnReqUser("cruser");
        config.setConnReqPassword("crpass");

        VendorProfile vendor = VendorProfile.defaultVendor(config);
        assertEquals(VendorProfile.DEFAULT_VENDOR_NAME, vendor.name());
        assertEquals("SimVendor", vendor.manufacturer());
        assertEquals("A1B2C3", vendor.oui());
        assertEquals("Router", vendor.productClass());
        assertEquals("cruser", vendor.connReqUser());
        assertTrue(vendor.hasConnReqAuth());
    }

    @Test
    void hasConnReqAuthRequiresBothFields() {
        VendorProfile noAuth = new VendorProfile("v1", "m", "000000", "PC", null, null);
        assertFalse(noAuth.hasConnReqAuth());
        VendorProfile halfAuth = new VendorProfile("v1", "m", "000000", "PC", "user", null);
        assertFalse(halfAuth.hasConnReqAuth());
    }

    @Test
    void rejectsBlankMandatoryFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new VendorProfile("", "m", "000000", "PC", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new VendorProfile("v1", "m", "", "PC", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new VendorProfile("v1", "m", "000000", "", null, null));
    }
}
