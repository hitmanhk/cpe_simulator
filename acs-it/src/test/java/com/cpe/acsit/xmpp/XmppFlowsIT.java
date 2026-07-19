package com.cpe.acsit.xmpp;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XMPP TLS handshake matrix and connection-request flows:
 * DIRECT/STARTTLS × PLAIN/SCRAM, XMPP-triggered CWMP sessions, NAT lazy
 * provisioning via SetParameterValues (TR-181 & TR-098), and reconnect.
 */
class XmppFlowsIT {

    // ---------------- TLS × SASL matrix ----------------

    @Test
    void directTlsWithScramSha256() throws Exception {
        runHandshake(XmppTestServer.TlsMode.DIRECT, "SCRAM-SHA-256");
    }

    @Test
    void directTlsWithPlain() throws Exception {
        runHandshake(XmppTestServer.TlsMode.DIRECT, "PLAIN");
    }

    @Test
    void startTlsWithScramSha1() throws Exception {
        runHandshake(XmppTestServer.TlsMode.STARTTLS, "SCRAM-SHA-1");
    }

    @Test
    void startTlsWithPlain() throws Exception {
        runHandshake(XmppTestServer.TlsMode.STARTTLS, "PLAIN");
    }

    // ---------------- XMPP connection request ----------------

    @Test
    void xmppConnectionRequestTriggersCwmpInform() throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com");
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
                });
                sim.start();
                ClientSession session = xmpp.awaitOnline(Duration.ofSeconds(30));

                // XMPP 回连 → IQ result → CWMP 6 CONNECTION REQUEST 会话
                CompletableFuture<StanzaView> ack =
                        xmpp.sendConnectionRequest(session, "user", "pass");
                StanzaView result = ack.get(30, TimeUnit.SECONDS);
                assertEquals("result", result.attr("type"),
                        "CPE should ack the connection request IQ");

                CwmpMessage inform = sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST",
                        Duration.ofSeconds(30));
                assertNotNull(inform.serialNumber());
            }
        } finally {
            xmpp.stop();
        }
    }

    // ---------------- NAT 懒连接（ACS 下发 XMPP 凭据） ----------------

    @Test
    void natLazyXmppConnectViaTr181Credentials() throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com");
        xmpp.start();
        try {
            try (SimHarness sim = new SimHarness()) {
                sim.configure(c -> {
                    c.setMode("nat"); // 非 xmpp-only：先 CWMP 直连，ACS 下发凭据后懒连 XMPP
                    c.getXmpp().setHost("127.0.0.1");
                    c.getXmpp().setPort(xmpp.getPort());
                    c.getXmpp().setDomain("iot.example.com");
                    c.getXmpp().setPassword("secret");
                });
                // BOOTSTRAP 会话内下发 XMPP 凭据
                CompletableFuture<CwmpMessage> set = sim.acs.setParameterValues(Map.of(
                        "Device.XMPP.Connection.1.Username", "lazy-user",
                        "Device.XMPP.Connection.1.Password", "secret"));
                sim.start();
                sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

                CwmpMessage setResponse = set.get(30, TimeUnit.SECONDS);
                assertFalse(setResponse.isFault());

                // 设备应懒建立 XMPP 连接并上线
                ClientSession session = xmpp.awaitOnline(Duration.ofSeconds(30));
                assertNotNull(session.jid());
            }
        } finally {
            xmpp.stop();
        }
    }

    @Test
    void natLazyXmppConnectViaTr098Credentials() throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com");
        xmpp.start();
        try {
            try (SimHarness sim = new SimHarness()) {
                sim.configure(c -> {
                    c.setMode("nat");
                    c.setModel(SimHarness.MODEL_TR098);
                    c.getXmpp().setHost("127.0.0.1");
                    c.getXmpp().setPort(xmpp.getPort());
                    c.getXmpp().setDomain("iot.example.com");
                    c.getXmpp().setPassword("secret");
                });
                CompletableFuture<CwmpMessage> set = sim.acs.setParameterValues(Map.of(
                        "InternetGatewayDevice.XMPP.Connection.1.Username", "lazy-user",
                        "InternetGatewayDevice.XMPP.Connection.1.Password", "secret"));
                sim.start();
                sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

                assertFalse(set.get(30, TimeUnit.SECONDS).isFault());
                ClientSession session = xmpp.awaitOnline(Duration.ofSeconds(30));
                assertNotNull(session.jid());
            }
        } finally {
            xmpp.stop();
        }
    }

    // ---------------- 断线重连 ----------------

    @Test
    void killedConnectionReconnectsWithBackoff() throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com");
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
                    c.getXmpp().setReconnectInitialDelay(1); // 加速重连
                    c.getXmpp().setReconnectMaxDelay(5);
                });
                sim.start();
                ClientSession first = xmpp.awaitOnline(Duration.ofSeconds(30));

                xmpp.killSession(first);

                ClientSession second = xmpp.awaitOnline(Duration.ofSeconds(30));
                assertTrue(second.id() != first.id(), "reconnect should open a new session");
                assertEquals(2, xmpp.sessions().size(), "server should have seen both attempts");
            }
        } finally {
            xmpp.stop();
        }
    }

    // ---------------- 认证失败路径 ----------------

    @Test
    void wrongPasswordNeverComesOnline() throws Exception {
        XmppTestServer xmpp = new XmppTestServer("iot.example.com");
        xmpp.start();
        try {
            try (SimHarness sim = new SimHarness()) {
                sim.configure(c -> {
                    c.setMode("nat");
                    c.setXmppOnly(true);
                    c.getXmpp().setHost("127.0.0.1");
                    c.getXmpp().setPort(xmpp.getPort());
                    c.getXmpp().setDomain("iot.example.com");
                    c.getXmpp().setPassword("wrong-secret");
                    c.getXmpp().setReconnectInitialDelay(1);
                });
                sim.start();

                Thread.sleep(6000);
                assertEquals(0, xmpp.onlineCount(),
                        "wrong SASL credentials must never reach ONLINE");
                assertTrue(xmpp.sessions().size() >= 1,
                        "server should have seen the failed attempts");
            }
        } finally {
            xmpp.stop();
        }
    }

    // ---------------- helper ----------------

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
                });
                sim.start();

                ClientSession session = xmpp.awaitOnline(Duration.ofSeconds(30));
                assertEquals(mechanism, session.mechanism());
                assertTrue(session.jid().endsWith("@iot.example.com/sim"),
                        "unexpected jid: " + session.jid());
            }
        } finally {
            xmpp.stop();
        }
    }
}
