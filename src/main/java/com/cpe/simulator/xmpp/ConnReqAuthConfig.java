package com.cpe.simulator.xmpp;

public record ConnReqAuthConfig(boolean enabled, String username, String password, String algorithm) {
    public ConnReqAuthConfig {
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = "MD5";
        }
    }
}