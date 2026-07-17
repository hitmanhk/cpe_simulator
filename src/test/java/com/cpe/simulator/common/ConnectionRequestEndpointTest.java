package com.cpe.simulator.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionRequestEndpointTest {

    @Test
    void wildcardListenerUsesInstanceLocalAddress() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("0.0.0.0");
        config.setConnReqPort(7547);
        config.setLocalAddress("192.0.2.11");

        assertEquals("192.0.2.11", ConnectionRequestEndpoint.resolveBindHost(config));
        assertEquals("192.0.2.11", ConnectionRequestEndpoint.resolveAdvertisedHost(config));
        assertEquals("http://192.0.2.11:7547/connReq/device-001",
            ConnectionRequestEndpoint.buildUrl(config, "device-001"));
    }

    @Test
    void explicitListenerTakesPrecedenceOverLocalAddress() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("192.0.2.20");
        config.setLocalAddress("192.0.2.11");

        assertEquals("192.0.2.20", ConnectionRequestEndpoint.resolveBindHost(config));
        assertEquals("192.0.2.20", ConnectionRequestEndpoint.resolveAdvertisedHost(config));
    }

    @Test
    void ipv6AddressIsBracketedInUrl() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("::");
        config.setLocalAddress("2001:db8::11");

        assertEquals("2001:db8::11", ConnectionRequestEndpoint.resolveBindHost(config));
        assertEquals("http://[2001:db8::11]:7547/connReq/device-001",
            ConnectionRequestEndpoint.buildUrl(config, "device-001"));
    }
}
