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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnReqAuthHandlerTest {

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

    private XmppStanza.IqStanza createConnReqIq(String connReqXml, String from, String to) {
        String iqXml = "<iq type=\"get\" id=\"cr1\" from=\"" + from + "\" to=\"" + to + "\">"
            + connReqXml + "</iq>";
        return new XmppStanza.IqStanza("cr1", "get", from, to, "connectionRequest", XmlUtil.NS_CWMP_XMPP_CONN_REQ, iqXml);
    }

    private XmppStanza.IqStanza createConnReqIq(String connReqXml) {
        return createConnReqIq(connReqXml, "acs@iot.example.com", "cpe001@iot.example.com/sim");
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
    void authDisabled_directAccept() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(false, null, null);
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void authEnabled_credentialsMatch_accepted() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username><password>secret</password>"
            + "</connectionRequest>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void authEnabled_usernameMismatch_rejected() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>wrong</username><password>secret</password>"
            + "</connectionRequest>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<error type=\"cancel\">"));
        assertTrue(sent.xml().contains("<not-authorized"));
        assertTrue(sent.xml().contains("<connectionRequest"));
        assertNull(callbackValue.get());
    }

    @Test
    void authEnabled_passwordMismatch_rejected() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username><password>wrong</password>"
            + "</connectionRequest>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<error type=\"cancel\">"));
        assertTrue(sent.xml().contains("<not-authorized"));
        assertTrue(sent.xml().contains("<connectionRequest"));
        assertNull(callbackValue.get());
    }

    @Test
    void authEnabled_noChildElements_rejected() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq(
            "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<error type=\"cancel\">"));
        assertTrue(sent.xml().contains("<not-authorized"));
        assertTrue(sent.xml().contains("<connectionRequest"));
        assertNull(callbackValue.get());
    }

    @Test
    void parseConnReqChildElements_withUsernameAndPassword() {
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, new ConnReqAuthConfig(true, "admin", "secret"));

        String rawXml = "<iq type=\"get\" id=\"cr1\" from=\"acs@iot.example.com\" to=\"cpe001@iot.example.com/sim\">"
            + "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username><password>secret</password>"
            + "</connectionRequest>"
            + "</iq>";

        Map<String, String> elements = handler.parseConnReqChildElements(rawXml);
        assertEquals("admin", elements.get("username"));
        assertEquals("secret", elements.get("password"));
    }

    @Test
    void parseConnReqChildElements_noChildElements() {
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, new ConnReqAuthConfig(true, "admin", "secret"));

        String rawXml = "<iq type=\"get\" id=\"cr1\">"
            + "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"
            + "</iq>";

        Map<String, String> elements = handler.parseConnReqChildElements(rawXml);
        assertNull(elements.get("username"));
        assertNull(elements.get("password"));
    }

    @Test
    void parseConnReqChildElements_onlyUsername() {
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, new ConnReqAuthConfig(true, "admin", "secret"));

        String rawXml = "<iq type=\"get\" id=\"cr1\">"
            + "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\">"
            + "<username>admin</username>"
            + "</connectionRequest>"
            + "</iq>";

        Map<String, String> elements = handler.parseConnReqChildElements(rawXml);
        assertEquals("admin", elements.get("username"));
        assertNull(elements.get("password"));
    }
}