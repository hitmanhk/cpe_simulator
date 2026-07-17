package com.cpe.simulator.common;

/**
 * Resolves the HTTP Connection Request endpoint for one simulator instance.
 *
 * <p>When the configured listen host is a wildcard, {@code local-address}
 * identifies both the interface to bind and the address advertised to the ACS.</p>
 */
public final class ConnectionRequestEndpoint {

    private static final String DEFAULT_BIND_HOST = "0.0.0.0";

    private ConnectionRequestEndpoint() {
    }

    public static String resolveBindHost(AppConfig config) {
        String listenHost = valueOrDefault(config.getConnReqHost(), DEFAULT_BIND_HOST);
        String localAddress = trimToNull(config.getLocalAddress());
        if (isWildcard(listenHost) && localAddress != null) {
            return localAddress;
        }
        return listenHost;
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
