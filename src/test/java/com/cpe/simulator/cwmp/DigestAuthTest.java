package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigestAuthTest {

    @Test
    void md5HexProducesCorrectHash() {
        String result = DigestAuth.md5Hex("test");
        assertEquals("098f6bcd4621d373cade4e832627b4f6", result);
    }

    @Test
    void md5HexHandlesEmptyString() {
        String result = DigestAuth.md5Hex("");
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result);
    }

    @Test
    void calculateDigestResponseWithoutQop() {
        String response = DigestAuth.calculateDigestResponse(
                "admin", "secret", "testrealm", "abc123",
                "/acs", "POST", null, null, null);
        assertNotNull(response);
        assertEquals(32, response.length());
    }

    @Test
    void calculateDigestResponseWithQopAuth() {
        String response = DigestAuth.calculateDigestResponse(
                "admin", "secret", "testrealm", "abc123",
                "/acs", "POST", "auth", "00000001", "cnonce123");
        assertNotNull(response);
        assertEquals(32, response.length());
    }

    @Test
    void calculateDigestResponseKnownValues() {
        String ha1 = DigestAuth.md5Hex("Mufasa:myhost@testrealm.com:Circle Of Life");
        String ha2 = DigestAuth.md5Hex("GET:/dir/index.html");
        String expected = DigestAuth.md5Hex(ha1 + ":dcd98b7102dd2f0e8b11d0f600bfb0c093:00000001:0a4f113b:auth:" + ha2);

        String response = DigestAuth.calculateDigestResponse(
                "Mufasa", "Circle Of Life", "myhost@testrealm.com",
                "dcd98b7102dd2f0e8b11d0f600bfb0c093",
                "/dir/index.html", "GET", "auth", "00000001", "0a4f113b");

        assertEquals(expected, response);
    }

    @Test
    void buildAuthorizationHeaderParsesChallenge() {
        String challenge = "Digest realm=\"testrealm\", nonce=\"abc123\", qop=\"auth\", opaque=\"xyz789\"";
        String header = DigestAuth.buildAuthorizationHeader(challenge, "admin", "password", "/acs", "POST");

        assertTrue(header.startsWith("Digest "));
        assertTrue(header.contains("username=\"admin\""));
        assertTrue(header.contains("realm=\"testrealm\""));
        assertTrue(header.contains("nonce=\"abc123\""));
        assertTrue(header.contains("uri=\"/acs\""));
        assertTrue(header.contains("response=\""));
        assertTrue(header.contains("qop=auth"));
        assertTrue(header.contains("nc=00000001"));
        assertTrue(header.contains("cnonce=\""));
        assertTrue(header.contains("opaque=\"xyz789\""));
    }

    @Test
    void buildAuthorizationHeaderWithoutQop() {
        String challenge = "Digest realm=\"myrealm\", nonce=\"nonce123\"";
        String header = DigestAuth.buildAuthorizationHeader(challenge, "user", "pass", "/path", "GET");

        assertTrue(header.startsWith("Digest "));
        assertTrue(header.contains("username=\"user\""));
        assertTrue(header.contains("realm=\"myrealm\""));
        assertTrue(header.contains("nonce=\"nonce123\""));
        assertFalse(header.contains("qop="));
        assertFalse(header.contains("nc="));
    }

    @Test
    void parseWwwAuthenticateHandlesMultipleParams() {
        var params = DigestAuth.parseWwwAuthenticate(
                "Digest realm=\"acs\", nonce=\"n1\", qop=\"auth\", opaque=\"o1\", algorithm=MD5");

        assertEquals("acs", params.get("realm"));
        assertEquals("n1", params.get("nonce"));
        assertEquals("auth", params.get("qop"));
        assertEquals("o1", params.get("opaque"));
        assertEquals("MD5", params.get("algorithm"));
    }

    @Test
    void parseWwwAuthenticateHandlesNullAndEmpty() {
        var params1 = DigestAuth.parseWwwAuthenticate(null);
        assertTrue(params1.isEmpty());

        var params2 = DigestAuth.parseWwwAuthenticate("");
        assertTrue(params2.isEmpty());
    }
}
