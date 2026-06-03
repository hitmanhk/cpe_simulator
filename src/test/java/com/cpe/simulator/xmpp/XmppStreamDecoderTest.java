package com.cpe.simulator.xmpp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmppStreamDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new XmppStreamDecoder());
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private void writeXml(String xml) {
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);
    }

    @Test
    void testDecodeCompleteIqStanza() {
        String iq = "<iq type=\"get\" id=\"ping1\" from=\"server.example.com\" to=\"cpe001@iot.example.com/sim\">"
            + "<ping xmlns=\"urn:xmpp:ping\"/></iq>";

        writeXml(iq);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.IqStanza.class, stanza);

        XmppStanza.IqStanza iqStanza = (XmppStanza.IqStanza) stanza;
        assertEquals("ping1", iqStanza.id());
        assertEquals("get", iqStanza.type());
        assertEquals("server.example.com", iqStanza.from());
        assertEquals("cpe001@iot.example.com/sim", iqStanza.to());
        assertEquals("ping", iqStanza.childElement());
        assertEquals("urn:xmpp:ping", iqStanza.childNamespace());
    }

    @Test
    void testDecodePresenceStanza() {
        String presence = "<presence type=\"unavailable\" from=\"user@example.com\"/>";

        writeXml(presence);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.PresenceStanza.class, stanza);

        XmppStanza.PresenceStanza presenceStanza = (XmppStanza.PresenceStanza) stanza;
        assertEquals("unavailable", presenceStanza.type());
        assertEquals("user@example.com", presenceStanza.from());
    }

    @Test
    void testDecodeStreamFeaturesWithMechanisms() {
        String streamOpen = "<stream:stream xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" "
            + "from=\"server.example.com\" id=\"stream1\" version=\"1.0\">";
        writeXml(streamOpen);

        XmppStanza openStanza = channel.readInbound();
        assertNotNull(openStanza);
        assertInstanceOf(XmppStanza.StreamOpen.class, openStanza);

        XmppStanza.StreamOpen streamOpenStanza = (XmppStanza.StreamOpen) openStanza;
        assertEquals("server.example.com", streamOpenStanza.from());
        assertEquals("stream1", streamOpenStanza.id());

        String features = "<stream:features>"
            + "<mechanisms xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"
            + "<mechanism>PLAIN</mechanism>"
            + "<mechanism>SCRAM-SHA-1</mechanism>"
            + "</mechanisms>"
            + "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>"
            + "<session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/>"
            + "</stream:features>";
        writeXml(features);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.StreamFeatures.class, stanza);

        XmppStanza.StreamFeatures sf = (XmppStanza.StreamFeatures) stanza;
        List<String> mechanisms = sf.mechanisms();
        assertEquals(2, mechanisms.size());
        assertTrue(mechanisms.contains("PLAIN"));
        assertTrue(mechanisms.contains("SCRAM-SHA-1"));
        assertTrue(sf.hasBind());
        assertTrue(sf.hasSession());
        assertFalse(sf.hasStartTls());
    }

    @Test
    void testDecodeAuthSuccess() {
        String streamOpen = "<stream:stream xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" "
            + "from=\"server.example.com\" id=\"stream1\" version=\"1.0\">";
        writeXml(streamOpen);
        channel.readInbound();

        String success = "<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>";
        writeXml(success);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.AuthSuccess.class, stanza);
    }

    @Test
    void testDecodeSelfClosingPresence() {
        String presence = "<presence/>";

        writeXml(presence);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.PresenceStanza.class, stanza);

        XmppStanza.PresenceStanza presenceStanza = (XmppStanza.PresenceStanza) stanza;
        assertNull(presenceStanza.type());
        assertNull(presenceStanza.from());
    }

    @Test
    void testDecodeFragmentedData() {
        writeXml("<iq type=\"re");
        assertNull(channel.readInbound());

        writeXml("sult\" id=\"bind1\">");
        assertNull(channel.readInbound());

        writeXml("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">"
            + "<jid>cpe001@iot.example.com/sim</jid>"
            + "</bind></iq>");

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.IqStanza.class, stanza);

        XmppStanza.IqStanza iqStanza = (XmppStanza.IqStanza) stanza;
        assertEquals("result", iqStanza.type());
        assertEquals("bind1", iqStanza.id());
    }

    @Test
    void testDecodeStreamClose() {
        String streamOpen = "<stream:stream xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" "
            + "from=\"server.example.com\" id=\"stream1\" version=\"1.0\">";
        writeXml(streamOpen);
        channel.readInbound();

        writeXml("</stream:stream>");

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.StreamClose.class, stanza);
    }

    @Test
    void testDecodeMessageWithBody() {
        String message = "<message type=\"chat\" from=\"user@example.com\" to=\"other@example.com\" id=\"msg1\">"
            + "<body>Hello World</body></message>";

        writeXml(message);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.MessageStanza.class, stanza);

        XmppStanza.MessageStanza msgStanza = (XmppStanza.MessageStanza) stanza;
        assertEquals("chat", msgStanza.type());
        assertEquals("user@example.com", msgStanza.from());
        assertEquals("other@example.com", msgStanza.to());
        assertEquals("Hello World", msgStanza.body());
    }

    @Test
    void testDecodeStartTlsProceed() {
        String proceed = "<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>";

        writeXml(proceed);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.StartTlsProceed.class, stanza);
    }

    @Test
    void testDecodeAuthFailure() {
        String failure = "<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"
            + "<not-authorized/></failure>";

        writeXml(failure);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.AuthFailure.class, stanza);
    }

    @Test
    void testDecodeChallengeWithPayload() {
        String challenge = "<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"
            + "cmVhbG09aW90LmV4YW1wbGUuY29tLG5vbmNlPWZjMDZhNjQ5LGNzPU...</challenge>";

        writeXml(challenge);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.AuthChallenge.class, stanza);

        XmppStanza.AuthChallenge authChallenge = (XmppStanza.AuthChallenge) stanza;
        assertNotNull(authChallenge.payload());
        assertTrue(authChallenge.payload().startsWith("cmVhbG"));
    }

    @Test
    void testDecodeChallengeEmptyPayload() {
        String challenge = "<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>";

        writeXml(challenge);

        XmppStanza stanza = channel.readInbound();
        assertNotNull(stanza);
        assertInstanceOf(XmppStanza.AuthChallenge.class, stanza);

        XmppStanza.AuthChallenge authChallenge = (XmppStanza.AuthChallenge) stanza;
        assertNull(authChallenge.payload());
    }
}
