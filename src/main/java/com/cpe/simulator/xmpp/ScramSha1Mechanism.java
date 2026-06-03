package com.cpe.simulator.xmpp;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class ScramSha1Mechanism implements SaslMechanism {

    private static final int ITERATION_COUNT = 4096;
    private static final int KEY_LENGTH = 160;

    private String clientNonce;
    private String clientFirstMessageBare;

    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-1";
    }

    @Override
    public String getInitialResponse(String username, String password) {
        SecureRandom random = new SecureRandom();
        byte[] nonceBytes = new byte[24];
        random.nextBytes(nonceBytes);
        clientNonce = Base64.getEncoder().encodeToString(nonceBytes);

        String escapedUsername = username.replace("=", "=3D").replace(",", "=2C");
        clientFirstMessageBare = "n=" + escapedUsername + ",r=" + clientNonce;

        String clientFirstMessage = "n,," + clientFirstMessageBare;
        return Base64.getEncoder().encodeToString(clientFirstMessage.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getChallengeResponse(String challenge, String username, String password) {
        try {
            String serverFirstMessage = new String(Base64.getDecoder().decode(challenge), StandardCharsets.UTF_8);

            String serverNonce = null;
            String salt = null;
            int iterations = ITERATION_COUNT;

            for (String part : serverFirstMessage.split(",")) {
                if (part.startsWith("r=")) {
                    serverNonce = part.substring(2);
                } else if (part.startsWith("s=")) {
                    salt = part.substring(2);
                } else if (part.startsWith("i=")) {
                    iterations = Integer.parseInt(part.substring(2));
                }
            }

            if (serverNonce == null || !serverNonce.startsWith(clientNonce)) {
                return null;
            }

            byte[] saltBytes = Base64.getDecoder().decode(salt);

            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), saltBytes, iterations, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] saltedPassword = factory.generateSecret(keySpec).getEncoded();

            byte[] clientKey = hmacSha1(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
            byte[] storedKey = sha1(clientKey);

            String channelBinding = "c=" + Base64.getEncoder().encodeToString("n,,".getBytes(StandardCharsets.UTF_8));
            String clientFinalWithoutProof = channelBinding + ",r=" + serverNonce;

            String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;
            byte[] authMessageBytes = authMessage.getBytes(StandardCharsets.UTF_8);

            byte[] clientSignature = hmacSha1(storedKey, authMessageBytes);
            byte[] clientProof = xor(clientKey, clientSignature);

            String clientFinalMessage = clientFinalWithoutProof + ",p=" +
                Base64.getEncoder().encodeToString(clientProof);

            return Base64.getEncoder().encodeToString(clientFinalMessage.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmacSha1(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        return mac.doFinal(data);
    }

    private byte[] sha1(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(data);
    }

    private byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }
}
