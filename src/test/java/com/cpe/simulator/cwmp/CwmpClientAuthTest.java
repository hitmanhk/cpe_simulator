package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CwmpClientAuthTest {

    @Test
    void buildBasicAuthHeaderEncodesCredentials() {
        String header = CwmpClient.buildBasicAuthHeader("admin", "secret");
        assertEquals("Basic YWRtaW46c2VjcmV0", header);
    }

    @Test
    void detectAuthSchemeDigest() {
        String scheme = CwmpClient.detectAuthScheme("Digest realm=\"test\", nonce=\"abc\"");
        assertEquals("Digest", scheme);
    }

    @Test
    void detectAuthSchemeBasic() {
        String scheme = CwmpClient.detectAuthScheme("Basic realm=\"test\"");
        assertEquals("Basic", scheme);
    }

    @Test
    void detectAuthSchemeNull() {
        String scheme = CwmpClient.detectAuthScheme(null);
        assertNull(scheme);
    }

    @Test
    void detectAuthSchemeCaseInsensitive() {
        assertEquals("Digest", CwmpClient.detectAuthScheme("digest realm=\"test\""));
        assertEquals("Basic", CwmpClient.detectAuthScheme("BASIC realm=\"test\""));
    }
}
