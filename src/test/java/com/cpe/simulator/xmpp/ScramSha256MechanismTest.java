package com.cpe.simulator.xmpp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ScramSha256MechanismTest {

    private static final String NONCE = "rOprNGfwEbeRWgbNEkqO";
    private static final String SERVER_FIRST = "r=" + NONCE
        + "%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096";

    @Test
    void matchesRfc7677Example() {
        ScramSha256Mechanism mechanism = new ScramSha256Mechanism(() -> NONCE);

        assertEquals("n,,n=user,r=" + NONCE,
            decode(mechanism.getInitialResponse("user", "pencil")));

        String response = mechanism.getChallengeResponse(encode(SERVER_FIRST), "user", "pencil");
        assertEquals("c=biws,r=" + NONCE
                + "%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=",
            decode(response));

        assertTrue(mechanism.verifyServerFinal(
            encode("v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=")));
    }

    @Test
    void rejectsServerNonceThatDoesNotExtendClientNonce() {
        ScramSha256Mechanism mechanism = new ScramSha256Mechanism(() -> NONCE);
        mechanism.getInitialResponse("user", "pencil");

        assertNull(mechanism.getChallengeResponse(
            encode("r=unrelated,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096"), "user", "pencil"));
    }

    @Test
    void rejectsInvalidServerSignature() {
        ScramSha256Mechanism mechanism = new ScramSha256Mechanism(() -> NONCE);
        mechanism.getInitialResponse("user", "pencil");
        assertNotNull(mechanism.getChallengeResponse(encode(SERVER_FIRST), "user", "pencil"));

        assertFalse(mechanism.verifyServerFinal(encode("v=AAAAAAAAAAAAAAAAAAAAAA==")));
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
