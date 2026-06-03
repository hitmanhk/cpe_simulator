package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PingHandlerTest {

    private XmppConnection connection;
    private AppConfig.XmppConfig config;
    private PingHandler pingHandler;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        config = new AppConfig.XmppConfig();
        config.setKeepaliveTimeout(5);

        connection = mock(XmppConnection.class);
        when(connection.getDeviceId()).thenReturn("001");
        when(connection.isOnline()).thenReturn(true);

        scheduler = Executors.newScheduledThreadPool(1);
        pingHandler = new PingHandler(connection, config);
        pingHandler.setScheduler(scheduler);
    }

    @Test
    void testPingResponse() {
        XmppStanza.IqStanza ping = new XmppStanza.IqStanza(
            "ping1", "get", "server.example.com", "cpe001@iot.example.com/sim",
            "ping", "urn:xmpp:ping", null);

        pingHandler.handlePing(ping);

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection).sendStanza(captor.capture());

        XmppStanza sent = captor.getValue();
        assertInstanceOf(XmppStanza.RawXml.class, sent);
        XmppStanza.RawXml raw = (XmppStanza.RawXml) sent;
        assertTrue(raw.xml().contains("type=\"result\""));
        assertTrue(raw.xml().contains("id=\"ping1\""));
    }

    @Test
    void testSendPing() {
        pingHandler.sendPing();

        ArgumentCaptor<XmppStanza> captor = ArgumentCaptor.forClass(XmppStanza.class);
        verify(connection).sendStanza(captor.capture());

        XmppStanza sent = captor.getValue();
        assertInstanceOf(XmppStanza.RawXml.class, sent);
        XmppStanza.RawXml raw = (XmppStanza.RawXml) sent;
        assertTrue(raw.xml().contains("urn:xmpp:ping"));
        assertNotNull(pingHandler.getLastPingId());
    }

    @Test
    void testPongCancelsTimeout() {
        pingHandler.sendPing();

        XmppStanza.IqStanza pong = new XmppStanza.IqStanza(
            pingHandler.getLastPingId(), "result", "server.example.com", null,
            null, null, null);

        pingHandler.handlePong(pong);

        verify(connection, never()).setState(XmppConnection.ConnectionState.FAILED);
    }

    @Test
    void testPingTimeoutDetection() throws Exception {
        config.setKeepaliveTimeout(1);
        pingHandler = new PingHandler(connection, config);
        pingHandler.setScheduler(scheduler);

        pingHandler.sendPing();

        Thread.sleep(2000);

        verify(connection).setState(XmppConnection.ConnectionState.FAILED);

        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
    }
}
