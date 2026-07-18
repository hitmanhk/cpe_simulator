package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class XmppConnectionTest {

    private AppConfig.XmppConfig config;

    @BeforeEach
    void setUp() {
        config = new AppConfig.XmppConfig();
        config.setHost("localhost");
        config.setPort(5222);
        config.setDomain("test.example.com");
        config.setUserPrefix("cpe");
        config.setPassword("secret");
    }

    @Test
    void testConstructorWithCallback() {
        AtomicReference<String> callbackResult = new AtomicReference<>();
        XmppConnection conn = new XmppConnection("device001", config, null, callbackResult::set, new ConnReqAuthConfig(false, null, null));

        assertEquals("device001", conn.getDeviceId());
        assertEquals(XmppConnection.ConnectionState.DISCONNECTED, conn.getState());
        assertFalse(conn.isOnline());
    }

    @Test
    void testConstructorWithNullCallback() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        assertNotNull(conn);
        assertEquals("device001", conn.getDeviceId());
    }

    @Test
    void testStateChangeCallbackRegistration() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<XmppConnection.ConnectionState> stateChanges = new ArrayList<>();

        conn.onStateChange(stateChanges::add);
        conn.setState(XmppConnection.ConnectionState.CONNECTING);
        conn.setState(XmppConnection.ConnectionState.AUTHENTICATING);
        conn.setState(XmppConnection.ConnectionState.ONLINE);

        assertEquals(3, stateChanges.size());
        assertEquals(XmppConnection.ConnectionState.CONNECTING, stateChanges.get(0));
        assertEquals(XmppConnection.ConnectionState.AUTHENTICATING, stateChanges.get(1));
        assertEquals(XmppConnection.ConnectionState.ONLINE, stateChanges.get(2));
    }

    @Test
    void testStateChangeWithoutCallback() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));

        assertDoesNotThrow(() -> {
            conn.setState(XmppConnection.ConnectionState.CONNECTING);
            conn.setState(XmppConnection.ConnectionState.ONLINE);
            conn.setState(XmppConnection.ConnectionState.FAILED);
        });

        assertEquals(XmppConnection.ConnectionState.FAILED, conn.getState());
    }

    @Test
    void testStateChangeCallbackReceivesAllStates() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<XmppConnection.ConnectionState> stateChanges = new ArrayList<>();
        conn.onStateChange(stateChanges::add);

        // Set states in sequence, skipping DISCONNECTED since it's the initial state
        // (setState is idempotent: same-state transitions are ignored)
        XmppConnection.ConnectionState[] states = {
            XmppConnection.ConnectionState.CONNECTING,
            XmppConnection.ConnectionState.AUTHENTICATING,
            XmppConnection.ConnectionState.AUTHENTICATED,
            XmppConnection.ConnectionState.BINDING,
            XmppConnection.ConnectionState.BOUND,
            XmppConnection.ConnectionState.ONLINE,
            XmppConnection.ConnectionState.FAILED,
            XmppConnection.ConnectionState.DISCONNECTED
        };

        for (XmppConnection.ConnectionState state : states) {
            conn.setState(state);
            assertEquals(state, conn.getState());
        }
        assertEquals(states.length, stateChanges.size());
        for (int i = 0; i < states.length; i++) {
            assertEquals(states[i], stateChanges.get(i));
        }
    }

    @Test
    void testJidGetterSetter() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        assertNull(conn.getJid());

        conn.setJid("device001@test.example.com/sim");
        assertEquals("device001@test.example.com/sim", conn.getJid());
    }

    @Test
    void testIsOnlineRequiresChannelAndState() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));

        conn.setState(XmppConnection.ConnectionState.ONLINE);
        assertFalse(conn.isOnline());
    }

    @Test
    void testSendStanzaWithNoChannel() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));

        assertDoesNotThrow(() ->
            conn.sendStanza(new XmppStanza.PresenceStanza(null, null, null, null))
        );
    }

    @Test
    void testDisconnectWithNoChannel() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<XmppConnection.ConnectionState> stateChanges = new ArrayList<>();
        conn.onStateChange(stateChanges::add);

        assertDoesNotThrow(conn::disconnect);
        assertEquals(XmppConnection.ConnectionState.DISCONNECTED, conn.getState());
        // disconnect() on an already-DISCONNECTED connection is idempotent,
        // so no state change callback is fired
        assertTrue(stateChanges.isEmpty());
    }

    @Test
    void testGetConfig() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        assertSame(config, conn.getConfig());
    }

    @Test
    void testOnFailureCallbackFiredOnFailedState() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureDeviceIds = new ArrayList<>();
        conn.onFailure(failureDeviceIds::add);

        conn.setState(XmppConnection.ConnectionState.FAILED);

        assertEquals(1, failureDeviceIds.size());
        assertEquals("device001", failureDeviceIds.get(0));
    }

    @Test
    void testOnFailureCallbackFiredOnDisconnectedState() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureDeviceIds = new ArrayList<>();
        conn.onFailure(failureDeviceIds::add);

        // Transition from a non-DISCONNECTED state so the idempotent guard doesn't skip it
        conn.setState(XmppConnection.ConnectionState.CONNECTING);
        conn.setState(XmppConnection.ConnectionState.DISCONNECTED);

        assertEquals(1, failureDeviceIds.size());
        assertEquals("device001", failureDeviceIds.get(0));
    }

    @Test
    void testOnSuccessCallbackFiredOnOnlineState() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> successDeviceIds = new ArrayList<>();
        conn.onSuccess(successDeviceIds::add);

        conn.setState(XmppConnection.ConnectionState.ONLINE);

        assertEquals(1, successDeviceIds.size());
        assertEquals("device001", successDeviceIds.get(0));
    }

    @Test
    void testCallbacksNotFiredForOtherStates() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureIds = new ArrayList<>();
        List<String> successIds = new ArrayList<>();
        conn.onFailure(failureIds::add);
        conn.onSuccess(successIds::add);

        conn.setState(XmppConnection.ConnectionState.CONNECTING);
        conn.setState(XmppConnection.ConnectionState.AUTHENTICATING);
        conn.setState(XmppConnection.ConnectionState.AUTHENTICATED);
        conn.setState(XmppConnection.ConnectionState.BINDING);
        conn.setState(XmppConnection.ConnectionState.BOUND);

        assertTrue(failureIds.isEmpty());
        assertTrue(successIds.isEmpty());
    }

    @Test
    void testCallbacksWorkWithoutRegistration() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));

        assertDoesNotThrow(() -> {
            conn.setState(XmppConnection.ConnectionState.FAILED);
            conn.setState(XmppConnection.ConnectionState.ONLINE);
            conn.setState(XmppConnection.ConnectionState.DISCONNECTED);
        });
    }

    @Test
    void testOnFailureNotFiredOnIntentionalDisconnect() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureIds = new ArrayList<>();
        conn.onFailure(failureIds::add);

        conn.disconnect();

        assertTrue(failureIds.isEmpty());
        assertEquals(XmppConnection.ConnectionState.DISCONNECTED, conn.getState());
    }

    @Test
    void testReconnectAfterIntentionalDisconnectAndExplicitStateChange() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureIds = new ArrayList<>();
        conn.onFailure(failureIds::add);

        conn.disconnect();
        assertTrue(failureIds.isEmpty());

        conn.setState(XmppConnection.ConnectionState.FAILED);
        assertEquals(1, failureIds.size());
        assertEquals("device001", failureIds.get(0));
    }

    @Test
    void testDisconnectRequestedClearedOnReconnect() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureIds = new ArrayList<>();
        conn.onFailure(failureIds::add);

        conn.disconnect();
        assertTrue(failureIds.isEmpty());

        // After disconnect, simulate a reconnect attempt
        // connect() should clear disconnectRequested, so a subsequent
        // failure should fire onFailure
        conn.setState(XmppConnection.ConnectionState.CONNECTING);
        conn.setState(XmppConnection.ConnectionState.FAILED);

        assertEquals(1, failureIds.size());
        assertEquals("device001", failureIds.get(0));
    }

    @Test
    void testDisconnectRequestedClearedAfterOnlineCycle() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        List<String> failureIds = new ArrayList<>();
        List<String> successIds = new ArrayList<>();
        conn.onFailure(failureIds::add);
        conn.onSuccess(successIds::add);

        conn.disconnect();
        assertTrue(failureIds.isEmpty());

        // Go through a full reconnect cycle
        conn.setState(XmppConnection.ConnectionState.CONNECTING);
        conn.setState(XmppConnection.ConnectionState.ONLINE);
        assertEquals(1, successIds.size());

        // Now an unplanned disconnect should fire onFailure
        conn.setState(XmppConnection.ConnectionState.DISCONNECTED);
        assertEquals(1, failureIds.size());
        assertEquals("device001", failureIds.get(0));
    }

    @Test
    void diagnosticSnapshotIncludesProtocolStateAndSafeEndpointDefaults() {
        XmppConnection conn = new XmppConnection(
            "device001", config, "127.0.0.2", null,
            new ConnReqAuthConfig(false, null, null));

        conn.setState(XmppConnection.ConnectionState.AUTHENTICATING);

        String snapshot = conn.diagnosticSnapshot();
        assertTrue(snapshot.contains("device=device001"));
        assertTrue(snapshot.contains("state=AUTHENTICATING"));
        assertTrue(snapshot.contains("channelActive=false"));
        assertTrue(snapshot.contains("local=127.0.0.2:auto"));
        assertTrue(snapshot.contains("remote=localhost:5222"));
        assertTrue(snapshot.contains("lastInbound=-/-1ms"));
        assertTrue(snapshot.contains("lastOutbound=-/-1ms"));
        assertFalse(snapshot.contains(config.getPassword()));
    }

    @Test
    void handshakeTimeoutFailsTcpConnectionThatNeverCompletesXmppHandshake() throws Exception {
        config.setHost("127.0.0.1");
        config.setHandshakeTimeout(1);
        CountDownLatch releaseAcceptedSocket = new CountDownLatch(1);

        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try (Socket ignored = server.accept()) {
                    releaseAcceptedSocket.await(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            });
            config.setPort(server.getLocalPort());

            NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                XmppConnection conn = new XmppConnection(
                    "device-timeout", config, null, null,
                    new ConnReqAuthConfig(false, null, null));
                CountDownLatch failed = new CountDownLatch(1);
                conn.onFailure(deviceId -> failed.countDown());

                conn.connect(eventLoopGroup).get(3, TimeUnit.SECONDS);

                assertTrue(failed.await(3, TimeUnit.SECONDS));
                assertEquals(XmppConnection.ConnectionState.FAILED, conn.getState());
            } finally {
                releaseAcceptedSocket.countDown();
                eventLoopGroup.shutdownGracefully().sync();
                acceptor.join(3000);
            }
        }
    }
}
