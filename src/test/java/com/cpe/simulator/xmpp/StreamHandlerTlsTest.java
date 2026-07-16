package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

class StreamHandlerTlsTest {

    private XmppConnection connection;
    private AppConfig.XmppConfig config;
    private StreamHandler streamHandler;
    private SaslHandler saslHandler;

    @BeforeEach
    void setUp() {
        connection = mock(XmppConnection.class);
        when(connection.getDeviceId()).thenReturn("device-1");
        config = new AppConfig.XmppConfig();
        config.setDomain("example.com");
        config.setTlsMode(AppConfig.XmppConfig.TlsMode.STARTTLS);
        streamHandler = new StreamHandler(connection, config);
        saslHandler = mock(SaslHandler.class);
        streamHandler.setSaslHandler(saslHandler);
    }

    @Test
    void requestsStartTlsBeforeSaslAuthentication() {
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("PLAIN"), false, false, true);

        streamHandler.handleStreamFeatures(features);

        verify(connection).setState(XmppConnection.ConnectionState.TLS_NEGOTIATING);
        verify(connection).sendStanza(any(XmppStanza.StartTls.class));
        verifyNoInteractions(saslHandler);
    }

    @Test
    void failsWhenStartTlsIsNotOffered() {
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("PLAIN"), false, false, false);

        streamHandler.handleStreamFeatures(features);

        verify(connection).setState(XmppConnection.ConnectionState.FAILED);
        verify(connection, never()).sendStanza(any());
        verifyNoInteractions(saslHandler);
    }

    @Test
    void restartsStreamOnlyAfterTlsHandshakeCompletes() {
        CompletableFuture<Void> handshake = new CompletableFuture<>();
        when(connection.upgradeToTls()).thenReturn(handshake);
        streamHandler.handleStreamFeatures(new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("PLAIN"), false, false, true));

        streamHandler.handleStartTlsProceed();
        verify(connection, times(1)).sendStanza(any(XmppStanza.StartTls.class));

        handshake.complete(null);

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection, times(2)).sendStanza(captor.capture());
        assertInstanceOf(XmppStanza.StreamOpen.class, captor.getAllValues().get(1));
    }

    @Test
    void marksConnectionFailedWhenTlsHandshakeFails() {
        CompletableFuture<Void> handshake = new CompletableFuture<>();
        when(connection.upgradeToTls()).thenReturn(handshake);
        streamHandler.handleStreamFeatures(new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("PLAIN"), false, false, true));
        streamHandler.handleStartTlsProceed();

        handshake.completeExceptionally(new IllegalStateException("handshake failed"));

        verify(connection).setState(XmppConnection.ConnectionState.FAILED);
    }
}
