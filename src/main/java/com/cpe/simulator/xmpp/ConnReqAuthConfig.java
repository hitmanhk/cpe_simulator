package com.cpe.simulator.xmpp;

import java.util.function.Function;

/**
 * Connection request authentication config for the XMPP path.
 * Optionally resolves per-device credentials (e.g. from the device's vendor
 * profile); falls back to the global username/password when the provider
 * returns null, matching the pre-provider behavior.
 */
public record ConnReqAuthConfig(boolean enabled, String username, String password,
                                Function<String, String[]> deviceCredentials) {

    public ConnReqAuthConfig(boolean enabled, String username, String password) {
        this(enabled, username, password, null);
    }

    /**
     * Resolves the effective credentials for a device: vendor-specific
     * credentials first, then the global config.
     *
     * @return {@code [username, password]}, or null when authentication is
     *         disabled for this device
     */
    public String[] credentialsFor(String deviceId) {
        if (deviceCredentials != null) {
            String[] creds = deviceCredentials.apply(deviceId);
            if (creds != null && creds.length == 2
                    && creds[0] != null && !creds[0].isEmpty()
                    && creds[1] != null && !creds[1].isEmpty()) {
                return creds;
            }
        }
        return enabled ? new String[]{username, password} : null;
    }
}
