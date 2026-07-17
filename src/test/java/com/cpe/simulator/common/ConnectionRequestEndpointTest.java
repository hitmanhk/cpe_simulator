package com.cpe.simulator.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionRequestEndpointTest {

    @Test
    void concreteHostIsUsedForAllInstanceNetworking() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("192.0.2.11");
        config.setConnReqPort(7547);

        assertEquals("192.0.2.11", ConnectionRequestEndpoint.resolveBindHost(config));
        assertEquals("192.0.2.11", ConnectionRequestEndpoint.resolveOutboundBindHost(config));
        assertEquals("192.0.2.11", ConnectionRequestEndpoint.resolveAdvertisedHost(config));
        assertEquals("http://192.0.2.11:7547/connReq/device-001",
            ConnectionRequestEndpoint.buildUrl(config, "device-001"));
    }

    @Test
    void wildcardHostDoesNotBindOutboundConnections() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("0.0.0.0");

        assertEquals("0.0.0.0", ConnectionRequestEndpoint.resolveBindHost(config));
        assertNull(ConnectionRequestEndpoint.resolveOutboundBindHost(config));
    }

    @Test
    void ipv6AddressIsBracketedInUrl() {
        AppConfig config = new AppConfig();
        config.setConnReqHost("2001:db8::11");

        assertEquals("2001:db8::11", ConnectionRequestEndpoint.resolveBindHost(config));
        assertEquals("2001:db8::11", ConnectionRequestEndpoint.resolveOutboundBindHost(config));
        assertEquals("http://[2001:db8::11]:7547/connReq/device-001",
            ConnectionRequestEndpoint.buildUrl(config, "device-001"));
    }
}
