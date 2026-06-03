package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaslHandlerTest {

    private XmppConnection connection;
    private AppConfig.XmppConfig config;
    private SaslHandler saslHandler;

    @BeforeEach
    void setUp() {
        config = new AppConfig.XmppConfig();
        config.setDomain("iot.example.com");
        config.setUserPrefix("cpe");
        config.setPassword("secret");
        config.setSaslMechanism("PLAIN");

        connection = mock(XmppConnection.class);
        when(connection.getDeviceId()).thenReturn("001");

        List<SaslMechanism> mechanisms = List.of(
            new ScramSha1Mechanism(),
            new PlainMechanism()
        );
        saslHandler = new SaslHandler(connection, config, mechanisms);
    }

    @Test
    void testPlainAuthFlow() {
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("PLAIN", "SCRAM-SHA-1"), false, false, false);

        saslHandler.startAuth(features);

        verify(connection).setState(XmppConnection.ConnectionState.AUTHENTICATING);

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection).sendStanza(captor.capture());

        XmppStanza sent = captor.getValue();
        assertInstanceOf(XmppStanza.AuthStanza.class, sent);
        XmppStanza.AuthStanza authStanza = (XmppStanza.AuthStanza) sent;
        assertEquals("PLAIN", authStanza.mechanism());
        assertNotNull(authStanza.payload());
        assertEquals(saslHandler.getSelectedMechanism().getMechanismName(), "PLAIN");
    }

    @Test
    void testAuthFailure() {
        XmppStanza.AuthFailure failure = new XmppStanza.AuthFailure("<not-authorized/>");

        saslHandler.handleAuthResult(failure);

        verify(connection).setState(XmppConnection.ConnectionState.FAILED);
        verify(connection, never()).sendStanza(any(XmppStanza.StreamOpen.class));
    }

    @Test
    void testAuthSuccess() {
        XmppStanza.AuthSuccess success = new XmppStanza.AuthSuccess(null);

        saslHandler.handleAuthResult(success);

        verify(connection).setState(XmppConnection.ConnectionState.AUTHENTICATED);

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection).sendStanza(captor.capture());
        assertInstanceOf(XmppStanza.StreamOpen.class, captor.getValue());
    }

    @Test
    void testNoCompatibleMechanism() {
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("EXTERNAL"), false, false, false);

        saslHandler.startAuth(features);

        verify(connection).setState(XmppConnection.ConnectionState.FAILED);
        verify(connection, never()).sendStanza(any());
    }
}
