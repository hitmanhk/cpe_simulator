package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

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

    @Test
    void testHandleChallengeScramSha1NormalPath() {
        SaslMechanism mockMechanism = mock(SaslMechanism.class);
        when(mockMechanism.getMechanismName()).thenReturn("MOCK-SCRAM");
        when(mockMechanism.getInitialResponse(anyString(), anyString())).thenReturn("initial-response");
        when(mockMechanism.getChallengeResponse(anyString(), anyString(), anyString())).thenReturn("challenge-response");

        config.setSaslMechanism("MOCK-SCRAM");
        List<SaslMechanism> mechanisms = List.of(mockMechanism);
        saslHandler = new SaslHandler(connection, config, mechanisms);

        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("MOCK-SCRAM"), false, false, false);
        saslHandler.startAuth(features);

        XmppStanza.AuthChallenge challenge = new XmppStanza.AuthChallenge("server-challenge-data");
        saslHandler.handleChallenge(challenge);

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection, times(2)).sendStanza(captor.capture());

        XmppStanza sent = captor.getAllValues().get(1);
        assertInstanceOf(XmppStanza.AuthResponse.class, sent);
        XmppStanza.AuthResponse authResponse = (XmppStanza.AuthResponse) sent;
        assertEquals("challenge-response", authResponse.payload());

        verify(mockMechanism).getChallengeResponse("server-challenge-data", "xmpp", "secret");
    }

    @Test
    void testHandleChallengeFailurePath() {
        SaslMechanism failingMechanism = new SaslMechanism() {
            @Override
            public String getMechanismName() { return "FAIL-MECH"; }
            @Override
            public String getInitialResponse(String username, String password) { return ""; }
            @Override
            public String getChallengeResponse(String challenge, String username, String password) { return null; }
        };

        List<SaslMechanism> mechanisms = List.of(failingMechanism);
        saslHandler = new SaslHandler(connection, config, mechanisms);

        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("FAIL-MECH"), false, false, false);
        saslHandler.startAuth(features);

        XmppStanza.AuthChallenge challenge = new XmppStanza.AuthChallenge("some-challenge-data");
        saslHandler.handleChallenge(challenge);

        verify(connection, times(1)).setState(XmppConnection.ConnectionState.FAILED);
        verify(connection, times(1)).sendStanza(any(XmppStanza.AuthStanza.class));
        verify(connection, never()).sendStanza(any(XmppStanza.AuthResponse.class));
    }

    @Test
    void testAuthSuccessResetsDecoder() {
        XmppStreamDecoder mockDecoder = mock(XmppStreamDecoder.class);
        ChannelPipeline mockPipeline = mock(ChannelPipeline.class);
        Channel mockChannel = mock(Channel.class);
        when(mockPipeline.get(XmppStreamDecoder.class)).thenReturn(mockDecoder);
        when(mockChannel.pipeline()).thenReturn(mockPipeline);
        when(connection.getChannel()).thenReturn(mockChannel);

        XmppStanza.AuthSuccess success = new XmppStanza.AuthSuccess(null);
        saslHandler.handleAuthResult(success);

        InOrder inOrder = inOrder(connection, mockDecoder);
        inOrder.verify(connection).setState(XmppConnection.ConnectionState.AUTHENTICATED);
        inOrder.verify(mockDecoder).reset();
        inOrder.verify(connection).sendStanza(any(XmppStanza.StreamOpen.class));
    }

    @Test
    void testAuthSuccessDecoderNotFoundInPipeline() {
        ChannelPipeline mockPipeline = mock(ChannelPipeline.class);
        Channel mockChannel = mock(Channel.class);
        when(mockPipeline.get(XmppStreamDecoder.class)).thenReturn(null);
        when(mockChannel.pipeline()).thenReturn(mockPipeline);
        when(connection.getChannel()).thenReturn(mockChannel);

        XmppStanza.AuthSuccess success = new XmppStanza.AuthSuccess(null);
        saslHandler.handleAuthResult(success);

        verify(connection).setState(XmppConnection.ConnectionState.AUTHENTICATED);
        verify(connection).sendStanza(any(XmppStanza.StreamOpen.class));
    }

    @Test
    void testAuthSuccessNoChannel() {
        when(connection.getChannel()).thenReturn(null);

        XmppStanza.AuthSuccess success = new XmppStanza.AuthSuccess(null);
        saslHandler.handleAuthResult(success);

        verify(connection).setState(XmppConnection.ConnectionState.AUTHENTICATED);
        verify(connection).sendStanza(any(XmppStanza.StreamOpen.class));
    }
}
