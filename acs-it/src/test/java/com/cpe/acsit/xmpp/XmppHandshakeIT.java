package com.cpe.acsit.xmpp;

import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XMPP handshake smoke test: real engine in xmpp-only mode connects to the
 * embedded XMPP server across the TLS × SASL matrix, binds the configured
 * resource, and exchanges keepalive pings.
 */
class XmppHandshakeIT {

    @Test
    void plainOverPlainTcp() throws Exception {
        runHandshake(XmppTestServer.TlsMode.NONE, "PLAIN");
    }

    @Test
    void scramSha1OverPlainTcp() throws Exception {
        runHandshake(XmppTestServer.TlsMode.NONE, "SCRAM-SHA-1");
    }

    @Test
    void scramSha256OverPlainTcp() throws Exception {
        runHandshake(XmppTestServer.TlsMode.NONE, "SCRAM-SHA-256");
    }

    private void runHandshake(XmppTestServer.TlsMode tlsMode, String mechanism) throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com").tlsMode(tlsMode);
        xmpp.start();
        try {
            try (SimHarness sim = new SimHarness()) {
                sim.configure(c -> {
                    c.setMode("nat");
                    c.setXmppOnly(true);
                    c.getXmpp().setHost("127.0.0.1");
                    c.getXmpp().setPort(xmpp.getPort());
                    c.getXmpp().setDomain("iot.example.com");
                    c.getXmpp().setPassword("secret");
                    c.getXmpp().setTlsMode(tlsMode.name());
                    c.getXmpp().setSaslMechanism(mechanism);
                    c.getXmpp().setKeepaliveInterval(2);
                    c.getXmpp().setKeepaliveTimeout(5);
                });
                sim.start();

                ClientSession session = xmpp.awaitOnline(Duration.ofSeconds(30));
                assertEquals(mechanism, session.mechanism());
                assertEquals("sim", session.resource());
                assertNotNull(session.jid());
                assertTrue(session.jid().endsWith("@iot.example.com/sim"),
                        "unexpected jid: " + session.jid());

                String ping = session.awaitStanza("urn:xmpp:ping", 15_000);
                assertNotNull(ping, "keepalive ping should arrive within interval");
                // pingCount 在 event loop 中自增，等待其生效
                SimHarness.awaitTrue(() -> session.pingCount() >= 1,
                        Duration.ofSeconds(10), "ping count >= 1");
            }
        } finally {
            xmpp.stop();
        }
    }
}
