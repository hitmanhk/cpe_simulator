package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.XmlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IqHandlerTest {

    @Mock
    private XmppConnection connection;

    private AppConfig.XmppConfig xmppConfig;
    private List<XmppStanza> sentStanzas;
    private ArgumentCaptor<XmppStanza> stanzaCaptor;

    @BeforeEach
    void setUp() {
        xmppConfig = new AppConfig.XmppConfig();
        xmppConfig.setDomain("iot.example.com");

        sentStanzas = new ArrayList<>();
        stanzaCaptor = ArgumentCaptor.forClass(XmppStanza.class);

        lenient().when(connection.getConfig()).thenReturn(xmppConfig);
        lenient().when(connection.getDeviceId()).thenReturn("000000-SimCPE-SIM00001");
        lenient().when(connection.getJid()).thenReturn("cpe001@iot.example.com/sim");
        lenient().doAnswer(inv -> {
            sentStanzas.add(inv.getArgument(0));
            return null;
        }).when(connection).sendStanza(stanzaCaptor.capture());
    }

    private XmppStanza.IqStanza createConnReqIq(String connReqContent) {
        String iqXml = "<iq type=\"get\" id=\"cr1\" from=\"acs@iot.example.com\" to=\"cpe001@iot.example.com/sim\">"
            + connReqContent + "</iq>";
        return new XmppStanza.IqStanza("cr1", "get", "acs@iot.example.com", "cpe001@iot.example.com/sim", "connectionRequest", XmlUtil.NS_CWMP_XMPP_CONN_REQ, iqXml);
    }

    private XmppStanza.RawXml getLastSentRawXml() {
        for (int i = sentStanzas.size() - 1; i >= 0; i--) {
            if (sentStanzas.get(i) instanceof XmppStanza.RawXml raw) {
                return raw;
            }
        }
        return null;
    }

    @Test
    void authDisabled_connReq_directCallback() {
        PingHandler pingHandler = new PingHandler(connection, xmppConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(false, null, null);
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(connection, authConfig);
        IqHandler iqHandler = new IqHandler(connection, callbackValue::set, pingHandler, authHandler);

        iqHandler.handleIq(createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"));

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void authEnabled_connReq_plaintextAccepted() {
        PingHandler pingHandler = new PingHandler(connection, xmppConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(connection, authConfig);
        IqHandler iqHandler = new IqHandler(connection, callbackValue::set, pingHandler, authHandler);

        iqHandler.handleIq(createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username><password>secret</password>"
            + "</connectionRequest>"));

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void authEnabled_connReq_plaintextRejected() {
        PingHandler pingHandler = new PingHandler(connection, xmppConfig);

        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(connection, authConfig);
        IqHandler iqHandler = new IqHandler(connection, null, pingHandler, authHandler);

        iqHandler.handleIq(createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username><password>wrong</password>"
            + "</connectionRequest>"));

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<error type=\"cancel\">"));
        assertTrue(sent.xml().contains("<not-authorized"));
    }

    @Test
    void pingErrorResponseStillCountsAsLiveness() {
        PingHandler pingHandler = mock(PingHandler.class);
        when(pingHandler.getLastPingId()).thenReturn("ping-1");
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(
            connection, new ConnReqAuthConfig(false, null, null));
        IqHandler iqHandler = new IqHandler(connection, null, pingHandler, authHandler);
        XmppStanza.IqStanza error = new XmppStanza.IqStanza(
            "ping-1", "error", "xmpp.example.com", null,
            "error", "urn:ietf:params:xml:ns:xmpp-stanzas",
            "<iq id=\"ping-1\" type=\"error\"><error type=\"cancel\"><service-unavailable/></error></iq>");

        iqHandler.handleIq(error);

        verify(pingHandler).handlePong(error);
        verify(connection, never()).sendStanza(any());
    }
}
