package com.cpe.simulator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimLogger {

    private static final Logger LOG = LoggerFactory.getLogger("SimLogger");
    private static volatile boolean verbose = false;
    private static final Set<String> deviceFilter = ConcurrentHashMap.newKeySet();

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static void configure(String level, String filter) {
        setVerbose("DEBUG".equalsIgnoreCase(level));
        clearDeviceFilters();
        if (filter == null || filter.isBlank()) {
            return;
        }
        for (String deviceId : filter.split(",")) {
            String trimmed = deviceId.trim();
            if (!trimmed.isEmpty()) {
                addDeviceFilter(trimmed);
            }
        }
    }

    public static void addDeviceFilter(String deviceId) {
        deviceFilter.add(deviceId);
    }

    public static void removeDeviceFilter(String deviceId) {
        deviceFilter.remove(deviceId);
    }

    public static void clearDeviceFilters() {
        deviceFilter.clear();
    }

    public static void device(String deviceId, String message, Object... args) {
        if (!deviceFilter.isEmpty() && !deviceFilter.contains(deviceId)) {
            return;
        }
        if (verbose || deviceFilter.contains(deviceId)) {
            LOG.info("[{}] " + message, prepend(deviceId, args));
        }
    }

    /**
     * Records a connection state transition. Connection state is operationally
     * important, so it is emitted at INFO even when verbose device logging is off.
     */
    public static void connectionState(String deviceId, String connectionType,
                                       Object previousState, Object currentState, String reason) {
        if (!deviceFilter.isEmpty() && !deviceFilter.contains(deviceId)) {
            return;
        }
        LOG.info("[{}] Connection state changed: type={} previous={} current={} reason={}",
            deviceId, connectionType, previousState, currentState, reason);
    }

    /**
     * Records TR-069 RPC metadata without logging the SOAP payload, which may
     * contain credentials or parameter values.
     */
    public static void cwmpRpc(String deviceId, String sessionId, String direction,
                               String method, String status, String details) {
        if (!deviceFilter.isEmpty() && !deviceFilter.contains(deviceId)) {
            return;
        }
        LOG.info("[{}] TR-069 RPC: session={} direction={} method={} status={} details={}",
            deviceId, sessionId, direction, method, status,
            details == null || details.isBlank() ? "-" : details);
    }

    /**
     * Records an operational XMPP event without logging stanza payloads or
     * credentials. These events are intentionally emitted at INFO so a
     * connection request can be correlated across the XMPP and CWMP paths in
     * production without enabling verbose logging for every device.
     */
    public static void xmppEvent(String deviceId, String event, String details) {
        if (!deviceFilter.isEmpty() && !deviceFilter.contains(deviceId)) {
            return;
        }
        LOG.info("[{}] XMPP event: event={} details={}", deviceId, event,
            details == null || details.isBlank() ? "-" : details);
    }

    public static void info(String message, Object... args) {
        LOG.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOG.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOG.error(message, args);
    }

    public static void error(String message, Throwable t) {
        LOG.error(message, t);
    }

    public static void debug(String message, Object... args) {
        LOG.debug(message, args);
    }

    private static Object[] prepend(String first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }
}
