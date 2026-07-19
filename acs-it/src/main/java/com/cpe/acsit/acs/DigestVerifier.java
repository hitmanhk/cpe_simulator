package com.cpe.acsit.acs;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side HTTP Digest verification (RFC 7616, qop=auth) used to validate
 * the simulator's DigestAuth client against both MD5 and SHA-256.
 */
public final class DigestVerifier {

    public final String realm;
    public final String nonce;
    public final String opaque;
    public final String algorithm;

    private DigestVerifier(String realm, String nonce, String opaque, String algorithm) {
        this.realm = realm;
        this.nonce = nonce;
        this.opaque = opaque;
        this.algorithm = algorithm;
    }

    public static DigestVerifier create(String algorithm) {
        return new DigestVerifier("ACS", "acsit-nonce-0123456789", "acsit-opaque", algorithm);
    }

    public String challenge() {
        return "Digest realm=\"" + realm + "\", nonce=\"" + nonce + "\", opaque=\"" + opaque
                + "\", qop=\"auth\", algorithm=" + algorithm;
    }

    /** Verifies an Authorization header value against the expected credentials. */
    public boolean verify(String authorization, String expectedUser, String expectedPassword, String httpMethod) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Digest ", 0, 7)) {
            return false;
        }
        Map<String, String> params = parseParams(authorization.substring(7));
        String username = params.get("username");
        String nonce = params.get("nonce");
        String uri = params.get("uri");
        String response = params.get("response");
        String qop = params.get("qop");
        String nc = params.get("nc");
        String cnonce = params.get("cnonce");
        if (username == null || nonce == null || uri == null || response == null) {
            return false;
        }
        if (!this.nonce.equals(nonce) || !expectedUser.equals(username)) {
            return false;
        }
        String algorithm = params.getOrDefault("algorithm", this.algorithm);
        String ha1 = hashHex(algorithm, username + ":" + realm + ":" + expectedPassword);
        String ha2 = hashHex(algorithm, httpMethod + ":" + uri);
        String expected = "auth".equals(qop)
                ? hashHex(algorithm, ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2)
                : hashHex(algorithm, ha1 + ":" + nonce + ":" + ha2);
        return expected.equalsIgnoreCase(response);
    }

    private static Map<String, String> parseParams(String value) {
        Map<String, String> params = new HashMap<>();
        for (String part : value.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String raw = part.substring(eq + 1).trim();
            if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            params.put(key, raw);
        }
        return params;
    }

    private static String hashHex(String algorithm, String data) {
        try {
            String jcaName = algorithm.toUpperCase(java.util.Locale.ROOT).replace("-", "");
            MessageDigest digest = MessageDigest.getInstance(jcaName);
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Digest algorithm unavailable: " + algorithm, e);
        }
    }
}
