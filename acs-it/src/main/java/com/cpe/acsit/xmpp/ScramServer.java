package com.cpe.acsit.xmpp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server side of SCRAM (RFC 5802) for SCRAM-SHA-1 and SCRAM-SHA-256.
 * Performs full proof verification so the simulator's SCRAM client is
 * genuinely exercised, and emits a server-final signature the client must
 * verify in turn.
 */
public class ScramServer {

    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    private final String mechanism;
    private final String hashName;
    private final byte[] salt;
    private final int iterations;
    private final String serverNonce;

    private String clientFirstBare;
    private String clientNonce;
    private String username;
    private String serverFirst;

    public ScramServer(String mechanism) {
        this(mechanism, 4096);
    }

    public ScramServer(String mechanism, int iterations) {
        this.mechanism = mechanism;
        this.hashName = mechanism.equals("SCRAM-SHA-256") ? "SHA-256" : "SHA-1";
        this.iterations = iterations;
        this.salt = new byte[16];
        new SecureRandom().nextBytes(this.salt);
        this.serverNonce = randomNonce();
    }

    public String mechanism() {
        return mechanism;
    }

    /** Processes the client's initial response, returns the server-first-message. */
    public String handleClientFirst(String clientFirst) throws AuthException {
        if (clientFirst == null || clientFirst.isEmpty()) {
            throw new AuthException("empty client-first");
        }
        String gs2Header;
        int comma = clientFirst.indexOf(',');
        if (comma < 0) {
            throw new AuthException("malformed client-first");
        }
        int secondComma = clientFirst.indexOf(',', comma + 1);
        if (secondComma < 0) {
            throw new AuthException("malformed client-first (gs2)");
        }
        gs2Header = clientFirst.substring(0, secondComma + 1);
        if (!gs2Header.startsWith("n") && !gs2Header.startsWith("y")) {
            throw new AuthException("unsupported gs2 header: " + gs2Header);
        }
        clientFirstBare = clientFirst.substring(secondComma + 1);
        Map<String, String> attrs = parseAttributes(clientFirstBare);
        username = attrs.get("n");
        clientNonce = attrs.get("r");
        if (username == null || clientNonce == null) {
            throw new AuthException("missing n= or r= in client-first");
        }
        serverFirst = "r=" + clientNonce + serverNonce
                + ",s=" + Base64.getEncoder().encodeToString(salt)
                + ",i=" + iterations;
        return serverFirst;
    }

    public String username() {
        return username;
    }

    /**
     * Verifies client-final; returns server-final-message with the server signature.
     */
    public String handleClientFinal(String clientFinal, String password) throws AuthException {
        int proofIdx = clientFinal.lastIndexOf(",p=");
        if (proofIdx < 0) {
            throw new AuthException("missing proof in client-final");
        }
        String withoutProof = clientFinal.substring(0, proofIdx);
        String proofBase64 = clientFinal.substring(proofIdx + 3);
        Map<String, String> attrs = parseAttributes(withoutProof);
        String nonce = attrs.get("r");
        if (nonce == null || !nonce.equals(clientNonce + serverNonce)) {
            throw new AuthException("nonce mismatch");
        }
        String channelBinding = attrs.get("c");
        if (channelBinding == null) {
            throw new AuthException("missing channel binding");
        }

        try {
            byte[] saltedPassword = hi(password.getBytes(StandardCharsets.UTF_8), salt, iterations);
            byte[] clientKey = hmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance(hashName).digest(clientKey);
            String authMessage = clientFirstBare + "," + serverFirst + "," + withoutProof;
            byte[] clientSignature = hmac(storedKey, authMessage);
            byte[] proof = Base64.getDecoder().decode(proofBase64);
            if (proof.length != clientSignature.length) {
                throw new AuthException("proof length mismatch");
            }
            byte[] recoveredClientKey = new byte[proof.length];
            for (int i = 0; i < proof.length; i++) {
                recoveredClientKey[i] = (byte) (proof[i] ^ clientSignature[i]);
            }
            byte[] recoveredStoredKey = MessageDigest.getInstance(hashName).digest(recoveredClientKey);
            if (!MessageDigest.isEqual(storedKey, recoveredStoredKey)) {
                throw new AuthException("proof verification failed (bad password?)");
            }
            byte[] serverKey = hmac(saltedPassword, "Server Key");
            byte[] serverSignature = hmac(serverKey, authMessage);
            return "v=" + Base64.getEncoder().encodeToString(serverSignature);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("scram error: " + e.getMessage());
        }
    }

    private byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("Hmac" + hashName.replace("-", ""));
        mac.init(new SecretKeySpec(key, "Hmac" + hashName.replace("-", "")));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /** PBKDF2 single-password Hi() per RFC 5802. */
    private byte[] hi(byte[] password, byte[] salt, int iterations) throws Exception {
        Mac mac = Mac.getInstance("Hmac" + hashName.replace("-", ""));
        mac.init(new SecretKeySpec(password, "Hmac" + hashName.replace("-", "")));
        byte[] saltWithIndex = new byte[salt.length + 4];
        System.arraycopy(salt, 0, saltWithIndex, 0, salt.length);
        saltWithIndex[salt.length + 3] = 1;
        byte[] u = mac.doFinal(saltWithIndex);
        byte[] result = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < result.length; j++) {
                result[j] ^= u[j];
            }
        }
        return result;
    }

    private static Map<String, String> parseAttributes(String message) {
        Map<String, String> attrs = new LinkedHashMap<>();
        for (String part : message.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                attrs.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return attrs;
    }

    private static String randomNonce() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
                .replace('+', '-').replace('/', '_');
    }
}
