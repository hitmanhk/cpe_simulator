package com.cpe.simulator.xmpp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

abstract class ScramMechanism implements SaslMechanism {

    private static final int DEFAULT_ITERATION_COUNT = 4096;

    private final String mechanismName;
    private final String hmacAlgorithm;
    private final String digestAlgorithm;
    private final Supplier<String> nonceSupplier;

    private String clientNonce;
    private String clientFirstMessageBare;
    private byte[] expectedServerSignature;
    private boolean serverFinalVerified;

    ScramMechanism(String mechanismName, String hmacAlgorithm, String digestAlgorithm) {
        this(mechanismName, hmacAlgorithm, digestAlgorithm, ScramMechanism::newNonce);
    }

    ScramMechanism(String mechanismName, String hmacAlgorithm, String digestAlgorithm,
                   Supplier<String> nonceSupplier) {
        this.mechanismName = mechanismName;
        this.hmacAlgorithm = hmacAlgorithm;
        this.digestAlgorithm = digestAlgorithm;
        this.nonceSupplier = nonceSupplier;
    }

    @Override
    public String getMechanismName() {
        return mechanismName;
    }

    @Override
    public String getInitialResponse(String username, String password) {
        clientNonce = nonceSupplier.get();
        expectedServerSignature = null;
        serverFinalVerified = false;
        String escapedUsername = username.replace("=", "=3D").replace(",", "=2C");
        clientFirstMessageBare = "n=" + escapedUsername + ",r=" + clientNonce;
        return encode("n,," + clientFirstMessageBare);
    }

    @Override
    public String getChallengeResponse(String challenge, String username, String password) {
        try {
            String decodedChallenge = decode(challenge);
            if (decodedChallenge.startsWith("v=") || decodedChallenge.startsWith("e=")) {
                if (!verifyDecodedServerFinal(decodedChallenge)) {
                    return null;
                }
                serverFinalVerified = true;
                return "";
            }

            String serverNonce = null;
            String salt = null;
            int iterations = DEFAULT_ITERATION_COUNT;
            for (String part : decodedChallenge.split(",")) {
                if (part.startsWith("r=")) {
                    serverNonce = part.substring(2);
                } else if (part.startsWith("s=")) {
                    salt = part.substring(2);
                } else if (part.startsWith("i=")) {
                    iterations = Integer.parseInt(part.substring(2));
                }
            }

            if (clientNonce == null || clientFirstMessageBare == null || serverNonce == null
                || !serverNonce.startsWith(clientNonce) || serverNonce.length() == clientNonce.length()
                || salt == null || iterations <= 0) {
                return null;
            }

            byte[] saltedPassword = hi(password.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(salt), iterations);
            byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
            byte[] storedKey = digest(clientKey);
            String clientFinalWithoutProof = "c=biws,r=" + serverNonce;
            String authMessage = clientFirstMessageBare + "," + decodedChallenge + "," + clientFinalWithoutProof;
            byte[] authMessageBytes = authMessage.getBytes(StandardCharsets.UTF_8);
            byte[] clientSignature = hmac(storedKey, authMessageBytes);
            byte[] clientProof = xor(clientKey, clientSignature);

            byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8));
            expectedServerSignature = hmac(serverKey, authMessageBytes);

            return encode(clientFinalWithoutProof + ",p=" + Base64.getEncoder().encodeToString(clientProof));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean verifyServerFinal(String payload) {
        if (expectedServerSignature == null) {
            return true;
        }
        if (serverFinalVerified) {
            return payload == null || payload.isBlank();
        }
        try {
            return payload != null && verifyDecodedServerFinal(decode(payload));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyDecodedServerFinal(String serverFinal) {
        if (serverFinal.startsWith("e=") || !serverFinal.startsWith("v=")) {
            return false;
        }
        try {
            byte[] actual = Base64.getDecoder().decode(serverFinal.substring(2));
            return MessageDigest.isEqual(expectedServerSignature, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] hi(byte[] password, byte[] salt, int iterations) throws Exception {
        byte[] saltAndBlock = ByteBuffer.allocate(salt.length + Integer.BYTES)
            .put(salt).putInt(1).array();
        byte[] u = hmac(password, saltAndBlock);
        byte[] result = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = hmac(password, u);
            for (int j = 0; j < result.length; j++) {
                result[j] ^= u[j];
            }
        }
        return result;
    }

    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(hmacAlgorithm);
        mac.init(new SecretKeySpec(key, hmacAlgorithm));
        return mac.doFinal(data);
    }

    private byte[] digest(byte[] data) throws Exception {
        return MessageDigest.getInstance(digestAlgorithm).digest(data);
    }

    private static byte[] xor(byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("SCRAM proof operands must have equal length");
        }
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = (byte) (left[i] ^ right[i]);
        }
        return result;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String newNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
