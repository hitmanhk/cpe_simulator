package com.cpe.simulator.xmpp;

public interface SaslMechanism {
    String getMechanismName();
    String getInitialResponse(String username, String password);
    String getChallengeResponse(String challenge, String username, String password);

    default boolean verifyServerFinal(String payload) {
        return true;
    }
}
