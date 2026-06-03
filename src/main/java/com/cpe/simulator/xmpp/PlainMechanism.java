package com.cpe.simulator.xmpp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PlainMechanism implements SaslMechanism {

    @Override
    public String getMechanismName() {
        return "PLAIN";
    }

    @Override
    public String getInitialResponse(String username, String password) {
        byte[] authBytes = ("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(authBytes);
    }

    @Override
    public String getChallengeResponse(String challenge, String username, String password) {
        return null;
    }
}
