package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.XmlUtil;
import com.cpe.simulator.cwmp.DigestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
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
        return new XmppStanza.IqStanza("cr1", "get", from, to, "connReq", XmlUtil.NS_CWMP_XMPP_CONN_REQ, iqXml);
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

    private Map<String, String> extractChallengeAttrs(String xml) {
        Map<String, String> attrs = new HashMap<>();
        int challengeIdx = xml.indexOf("<challenge");
        if (challengeIdx < 0) return attrs;
        int challengeEnd = xml.indexOf("/>", challengeIdx);
        if (challengeEnd < 0) challengeEnd = xml.indexOf(">", challengeIdx);
        String challengeTag = xml.substring(challengeIdx, challengeEnd + 2);
        attrs.put("nonce", XmppStreamDecoder.extractAttribute(challengeTag, "nonce"));
        attrs.put("realm", XmppStreamDecoder.extractAttribute(challengeTag, "realm"));
        attrs.put("algorithm", XmppStreamDecoder.extractAttribute(challengeTag, "algorithm"));
        return attrs;
    }

    @Test
    void authDisabled_directAccept() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(false, null, null, "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        XmppStanza.IqStanza iq = createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>");

        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void noAuthAttributes_challenged() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        XmppStanza.IqStanza iq = createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>");

        handler.handle(iq, null);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        String xml = sent.xml();
        assertTrue(xml.contains("type=\"error\""));
        assertTrue(xml.contains("<challenge"));
        assertTrue(xml.contains("algorithm=\"MD5\""));
        assertTrue(xml.contains("nonce=\""));
        assertTrue(xml.contains("realm=\"iot.example.com\""));
        assertTrue(xml.contains("<not-authorized"));
        assertTrue(xml.contains(XmlUtil.NS_CWMP_XMPP_CONN_REQ));
    }

    @Test
    void correctResponse_accepted() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        assertNotNull(challengeXml);

        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");
        assertNotNull(nonce);
        assertEquals("iot.example.com", realm);

        String expectedResponse = DigestAuth.calculateDigestResponse(
            "MD5", "admin", "secret", realm, nonce,
            "cpe001@iot.example.com/sim", "GET", null, null, null);

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"" + expectedResponse + "\""
            + " nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"MD5\"/>";

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithAuth), callbackValue::set);

        XmppStanza.RawXml resultXml = getLastSentRawXml();
        assertNotNull(resultXml);
        assertTrue(resultXml.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void wrongResponse_rejected() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");

        String connReqWithBadAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"wrongresponse\""
            + " nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"MD5\"/>";

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithBadAuth), callbackValue::set);

        XmppStanza.RawXml errorXml = getLastSentRawXml();
        assertNotNull(errorXml);
        assertTrue(errorXml.xml().contains("type=\"error\""));
        assertTrue(errorXml.xml().contains("<not-authorized"));
        assertNull(callbackValue.get());
    }

    @Test
    void nonceMismatch_rejected() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        String connReqWithWrongNonce = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"abc\" nonce=\"wrongnonce\" realm=\"iot.example.com\" algorithm=\"MD5\"/>";

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithWrongNonce), callbackValue::set);

        XmppStanza.RawXml errorXml = getLastSentRawXml();
        assertNotNull(errorXml);
        assertTrue(errorXml.xml().contains("type=\"error\""));
        assertTrue(errorXml.xml().contains("<not-authorized"));
        assertFalse(errorXml.xml().contains("<challenge"));
        assertNull(callbackValue.get());
    }

    @Test
    void pendingNonceNull_challenged() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"abc\" nonce=\"somenonce\" realm=\"iot.example.com\" algorithm=\"MD5\"/>";

        handler.handle(createConnReqIq(connReqWithAuth), null);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<challenge"));
    }

    @Test
    void nonceExpired_rechallenged() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");

        handler.forceNonceExpiry();

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"expiredresponse\" nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"MD5\"/>";

        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithAuth), null);

        XmppStanza.RawXml sent = getLastSentRawXml();
        assertNotNull(sent);
        assertTrue(sent.xml().contains("type=\"error\""));
        assertTrue(sent.xml().contains("<challenge"));
    }

    @Test
    void sha256Verification() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "SHA-256");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        assertTrue(challengeXml.xml().contains("algorithm=\"SHA-256\""));

        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");

        String expectedResponse = DigestAuth.calculateDigestResponse(
            "SHA-256", "admin", "secret", realm, nonce,
            "cpe001@iot.example.com/sim", "GET", null, null, null);

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"" + expectedResponse + "\""
            + " nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"SHA-256\"/>";

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithAuth), callbackValue::set);

        XmppStanza.RawXml resultXml = getLastSentRawXml();
        assertTrue(resultXml.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void qopNull_simplifiedFormula() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");

        String expectedResponse = DigestAuth.calculateDigestResponse(
            "MD5", "admin", "secret", realm, nonce,
            "cpe001@iot.example.com/sim", "GET", null, null, null);

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"" + expectedResponse + "\""
            + " nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"MD5\"/>";

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(createConnReqIq(connReqWithAuth), callbackValue::set);

        XmppStanza.RawXml resultXml = getLastSentRawXml();
        assertTrue(resultXml.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void iqToNull_fallbackToConnectionJid() {
        ConnReqAuthConfig authConfig = new ConnReqAuthConfig(true, "admin", "secret", "MD5");
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, authConfig);

        handler.handle(createConnReqIq("<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"), null);

        XmppStanza.RawXml challengeXml = getLastSentRawXml();
        Map<String, String> challengeAttrs = extractChallengeAttrs(challengeXml.xml());
        String nonce = challengeAttrs.get("nonce");
        String realm = challengeAttrs.get("realm");

        String expectedResponse = DigestAuth.calculateDigestResponse(
            "MD5", "admin", "secret", realm, nonce,
            "cpe001@iot.example.com/sim", "GET", null, null, null);

        String connReqWithAuth = "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\""
            + " username=\"admin\" response=\"" + expectedResponse + "\""
            + " nonce=\"" + nonce + "\" realm=\"" + realm + "\" algorithm=\"MD5\"/>";

        String iqXml = "<iq type=\"get\" id=\"cr1\" from=\"acs@iot.example.com\">"
            + connReqWithAuth + "</iq>";
        XmppStanza.IqStanza iq = new XmppStanza.IqStanza("cr1", "get", "acs@iot.example.com", null, "connReq", XmlUtil.NS_CWMP_XMPP_CONN_REQ, iqXml);

        AtomicReference<String> callbackValue = new AtomicReference<>();
        sentStanzas.clear();
        handler.handle(iq, callbackValue::set);

        XmppStanza.RawXml resultXml = getLastSentRawXml();
        assertTrue(resultXml.xml().contains("type=\"result\""));
        assertEquals("000000-SimCPE-SIM00001", callbackValue.get());
    }

    @Test
    void parseConnReqAttrs_withAuthAttributes() {
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, new ConnReqAuthConfig(true, "admin", "secret", "MD5"));

        String rawXml = "<iq type=\"get\" id=\"cr1\" from=\"acs@iot.example.com\" to=\"cpe001@iot.example.com/sim\">"
            + "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\" username=\"admin\" response=\"abc123\" nonce=\"n1\" realm=\"iot.example.com\" algorithm=\"MD5\" qop=\"auth\" nc=\"00000001\" cnonce=\"c1\"/>"
            + "</iq>";

        Map<String, String> attrs = handler.parseConnReqAttrs(rawXml);
        assertEquals("admin", attrs.get("username"));
        assertEquals("abc123", attrs.get("response"));
        assertEquals("n1", attrs.get("nonce"));
        assertEquals("iot.example.com", attrs.get("realm"));
        assertEquals("MD5", attrs.get("algorithm"));
        assertEquals("auth", attrs.get("qop"));
        assertEquals("00000001", attrs.get("nc"));
        assertEquals("c1", attrs.get("cnonce"));
    }

    @Test
    void parseConnReqAttrs_noAuthAttributes() {
        ConnReqAuthHandler handler = new ConnReqAuthHandler(connection, new ConnReqAuthConfig(true, "admin", "secret", "MD5"));

        String rawXml = "<iq type=\"get\" id=\"cr1\">"
            + "<connReq xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>"
            + "</iq>";

        Map<String, String> attrs = handler.parseConnReqAttrs(rawXml);
        assertNull(attrs.get("username"));
        assertNull(attrs.get("response"));
    }
}