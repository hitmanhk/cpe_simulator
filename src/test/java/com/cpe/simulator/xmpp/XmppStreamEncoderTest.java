package com.cpe.simulator.xmpp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class XmppStreamEncoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new XmppStreamEncoder());
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private String readOutboundXml() {
        ByteBuf buf = channel.readOutbound();
        if (buf == null) return null;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void testEncodeAuthResponseWithPayload() {
        XmppStanza.AuthResponse response = new XmppStanza.AuthResponse("Yz1iaixyPWZjMDZhNjQ5LX...");
        channel.writeOutbound(response);

        String xml = readOutboundXml();
        assertEquals("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">Yz1iaixyPWZjMDZhNjQ5LX...</response>", xml);
    }

    @Test
    void testEncodeAuthResponseNullPayload() {
        XmppStanza.AuthResponse response = new XmppStanza.AuthResponse(null);
        channel.writeOutbound(response);

        String xml = readOutboundXml();
        assertEquals("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>", xml);
    }

    @Test
    void testEncodeAuthStanza() {
        XmppStanza.AuthStanza auth = new XmppStanza.AuthStanza("PLAIN", "dXNlcg==");
        channel.writeOutbound(auth);

        String xml = readOutboundXml();
        assertEquals("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"PLAIN\">dXNlcg==</auth>", xml);
    }
}