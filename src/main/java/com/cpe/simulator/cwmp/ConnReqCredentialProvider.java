package com.cpe.simulator.cwmp;

/**
 * Optional per-device connection-request credential provider. When present,
 * the connection request server asks it for the device's expected credentials
 * (e.g. from the device's vendor profile); returning {@code null} falls back
 * to the global {@code conn-req-user}/{@code conn-req-password} config.
 */
@FunctionalInterface
public interface ConnReqCredentialProvider {

    /**
     * @param deviceId the device extracted from the request URL
     * @return {@code [username, password]} for this device, or null to use
     *         the global config credentials
     */
    String[] credentialsFor(String deviceId);
}
