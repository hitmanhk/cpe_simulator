package com.cpe.simulator.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * Structured engine event for external observers (e.g. the desktop UI).
 * Events are published by {@link SimEventHub}; the engine itself does not
 * depend on any listener being present.
 */
public record SimEvent(Instant timestamp, Category category, String deviceId,
                       String type, Map<String, String> attributes) {

    public enum Category {
        /** CpeDevice state transitions (CREATED/CONNECTING/ONLINE/OFFLINE). */
        DEVICE_STATE,
        /** XMPP connection state transitions. */
        CONNECTION_STATE,
        /** TR-069 RPC message metadata (direction/method/status). */
        CWMP_RPC,
        /** Operational XMPP events (TCP/TLS/handshake/connection-request). */
        XMPP_EVENT
    }

    public static SimEvent of(Category category, String deviceId, String type,
                              Map<String, String> attributes) {
        return new SimEvent(Instant.now(), category, deviceId, type, attributes);
    }

    public String attr(String key) {
        return attributes.get(key);
    }
}
