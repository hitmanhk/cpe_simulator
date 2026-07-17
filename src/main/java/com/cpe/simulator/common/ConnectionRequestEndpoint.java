package com.cpe.simulator.common;

/**
 * Resolves the unified network address for one simulator instance.
 * A concrete {@code conn-req-host} is used for outbound connections, the HTTP
 * Connection Request listener, and the address advertised to the ACS.
 */
public final class ConnectionRequestEndpoint {

    private static final String DEFAULT_BIND_HOST = "0.0.0.0";

    private ConnectionRequestEndpoint() {
    }

    public static String resolveBindHost(AppConfig config) {
        return valueOrDefault(config.getConnReqHost(), DEFAULT_BIND_HOST);
    }

    public static String resolveOutboundBindHost(AppConfig config) {
        String host = resolveBindHost(config);
        return isWildcard(host) ? null : stripIpv6Brackets(host);
    }

    public static String resolveAdvertisedHost(AppConfig config) {
        String bindHost = resolveBindHost(config);
        return stripIpv6Brackets(bindHost);
    }

    public static String buildUrl(AppConfig config, String deviceId) {
        String host = resolveAdvertisedHost(config);
        if (host.indexOf(':') >= 0) {
            host = "[" + host + "]";
        }
        return String.format("http://%s:%d/connReq/%s",
            host, config.getConnReqPort(), deviceId);
    }

    static boolean isWildcard(String host) {
        String normalized = stripIpv6Brackets(host.trim());
        return "0.0.0.0".equals(normalized)
            || "::".equals(normalized)
            || "0:0:0:0:0:0:0:0".equals(normalized)
            || "*".equals(normalized);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : defaultValue;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
