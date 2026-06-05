package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
        AtomicReference<XmppConnection.ConnectionState> lastState = new AtomicReference<>();
        conn.onStateChange(lastState::set);

        for (XmppConnection.ConnectionState state : XmppConnection.ConnectionState.values()) {
            conn.setState(state);
            assertEquals(state, lastState.get());
            assertEquals(state, conn.getState());
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
        assertTrue(stateChanges.contains(XmppConnection.ConnectionState.DISCONNECTED));
    }

    @Test
    void testGetConfig() {
        XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
        assertSame(config, conn.getConfig());
    }
}
