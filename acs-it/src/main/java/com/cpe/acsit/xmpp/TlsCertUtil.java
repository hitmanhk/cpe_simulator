package com.cpe.acsit.xmpp;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Programmatic self-signed certificate for the test server's TLS endpoint.
 * (Netty's SelfSignedCertificate relies on JDK-internal sun.security APIs
 * that are inaccessible on JDK 21, so we generate via BouncyCastle instead.)
 */
public final class TlsCertUtil {

    public record SelfSignedCert(PrivateKey privateKey, X509Certificate certificate) {
    }

    private TlsCertUtil() {
    }

    public static SelfSignedCert generate(String commonName) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            X500Name name = new X500Name("CN=" + commonName);
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minus(1, ChronoUnit.HOURS)),
                    Date.from(now.plus(3650, ChronoUnit.DAYS)),
                    name,
                    keyPair.getPublic());
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(
                            new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
            return new SelfSignedCert(keyPair.getPrivate(), certificate);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate self-signed certificate", e);
        }
    }
}
