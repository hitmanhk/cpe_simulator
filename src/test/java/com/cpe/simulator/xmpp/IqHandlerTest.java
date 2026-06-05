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

    private XmppStanza.IqStanza createConnReqIq() {
        String iqXml = "<iq type=\"get\" id=\"cr1\" from=\"acs@iot.example.com\" to=\"cpe001@iot.example.com/sim\">"
            + "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"
            + "</iq>";
        return new XmppStanza.IqStanza("cr1", "get", "acs@iot.example.com", "cpe001@iot.example.com/sim", "connReq", XmlUtil.NS_CWMP_XMPP_CONN_REQ, iqXml);
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
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(false, null, null, "MD5");
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(connection, authConfig);
        PingHandler pingHandler = new PingHandler(connection, xmppConfig);
        IqHandler iqHandler = new IqHandler(connection, null, pingHandler, authHandler);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        ConnReqAuthConfig enabledConfig = new ConnReqAuthConfig(false, null, null, "MD5");
        ConnReqAuthHandler enabledAuthHandler = new ConnReqAuthHandler(connection, enabledConfig);
        IqHandler enabledIqHandler = new IqHandler(connection, callbackValue::set, pingHandler, enabledAuthHandler);

        enabledIqHandler.handleIq(createConnReqIq());

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void authEnabled_connReq_delegatesToAuthHandler() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler authHandler = new ConnReqAuthHandler(connection, authConfig);
        PingHandler pingHandler = new PingHandler(connection, xmppConfig);
        IqHandler iqHandler = new IqHandler(connection, null, pingHandler, authHandler);

        iqHandler.handleIq(createConnReqIq());

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<challenge"));
    }
}