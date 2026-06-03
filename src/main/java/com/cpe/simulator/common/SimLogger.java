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
