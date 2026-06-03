package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.nio.charset.StandardCharsets;

public class XmppStreamEncoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof XmppStanza stanza)) {
            ctx.write(msg, promise);
            return;
        }

        String xml = encode(stanza);
        if (xml != null) {
            ByteBuf buf = ctx.alloc().buffer(xml.length());
            buf.writeBytes(xml.getBytes(StandardCharsets.UTF_8));
            ctx.write(buf, promise);
        } else {
            promise.setSuccess();
        }
    }

    private String encode(XmppStanza stanza) {
        return switch (stanza) {
            case XmppStanza.StreamOpen so -> encodeStreamOpen(so);
            case XmppStanza.StreamClose ignored -> "</stream:stream>";
            case XmppStanza.AuthStanza auth -> encodeAuth(auth);
            case XmppStanza.AuthResponse resp -> encodeAuthResponse(resp);
            case XmppStanza.IqStanza iq -> encodeIq(iq);
            case XmppStanza.PresenceStanza p -> encodePresence(p);
            case XmppStanza.MessageStanza m -> encodeMessage(m);
            case XmppStanza.BindStanza b -> encodeBind(b);
            case XmppStanza.StartTls ignored -> "<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>";
            case XmppStanza.RawXml raw -> raw.xml();
            default -> null;
        };
    }

    private String encodeStreamOpen(XmppStanza.StreamOpen so) {
        StringBuilder sb = new StringBuilder();
        sb.append("<stream:stream xmlns=\"").append(XmlUtil.NS_XMPP_CLIENT).append("\"");
        sb.append(" xmlns:stream=\"").append(XmlUtil.NS_XMPP_STREAM).append("\"");
        if (so.to() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(so.to())).append("\"");
        }
        sb.append(" version=\"1.0\">");
        return sb.toString();
    }

    private String encodeAuth(XmppStanza.AuthStanza auth) {
        StringBuilder sb = new StringBuilder();
        sb.append("<auth xmlns=\"").append(XmlUtil.NS_XMPP_SASL).append("\"");
        sb.append(" mechanism=\"").append(XmlUtil.escapeXml(auth.mechanism())).append("\"");
        if (auth.payload() != null && !auth.payload().isEmpty()) {
            sb.append(">").append(auth.payload()).append("</auth>");
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }

    private String encodeIq(XmppStanza.IqStanza iq) {
        if (iq.rawXml() != null && !iq.rawXml().isEmpty()) {
            return iq.rawXml();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<iq");
        if (iq.type() != null) {
            sb.append(" type=\"").append(XmlUtil.escapeXml(iq.type())).append("\"");
        }
        if (iq.id() != null) {
            sb.append(" id=\"").append(XmlUtil.escapeXml(iq.id())).append("\"");
        }
        if (iq.to() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(iq.to())).append("\"");
        }
        if (iq.from() != null) {
            sb.append(" from=\"").append(XmlUtil.escapeXml(iq.from())).append("\"");
        }
        sb.append("/>");
        return sb.toString();
    }

    private String encodePresence(XmppStanza.PresenceStanza p) {
        if (p.type() == null) {
            return "<presence/>";
        }
        return "<presence type=\"" + XmlUtil.escapeXml(p.type()) + "\"/>";
    }

    private String encodeMessage(XmppStanza.MessageStanza m) {
        StringBuilder sb = new StringBuilder();
        sb.append("<message");
        if (m.type() != null) {
            sb.append(" type=\"").append(XmlUtil.escapeXml(m.type())).append("\"");
        }
        if (m.to() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(m.to())).append("\"");
        }
        if (m.from() != null) {
            sb.append(" from=\"").append(XmlUtil.escapeXml(m.from())).append("\"");
        }
        if (m.id() != null) {
            sb.append(" id=\"").append(XmlUtil.escapeXml(m.id())).append("\"");
        }
        if (m.body() != null) {
            sb.append("><body>").append(XmlUtil.escapeXml(m.body())).append("</body></message>");
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }

    private String encodeBind(XmppStanza.BindStanza b) {
        StringBuilder sb = new StringBuilder();
        sb.append("<iq type=\"set\"");
        if (b.id() != null) {
            sb.append(" id=\"").append(XmlUtil.escapeXml(b.id())).append("\"");
        }
        sb.append("><bind xmlns=\"").append(XmlUtil.NS_XMPP_BIND).append("\">");
        if (b.resource() != null) {
            sb.append("<resource>").append(XmlUtil.escapeXml(b.resource())).append("</resource>");
        }
        sb.append("</bind></iq>");
        return sb.toString();
    }

    private String encodeAuthResponse(XmppStanza.AuthResponse resp) {
        StringBuilder sb = new StringBuilder();
        sb.append("<response xmlns=\"").append(XmlUtil.NS_XMPP_SASL).append("\"");
        if (resp.payload() != null) {
            sb.append(">").append(resp.payload()).append("</response>");
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }
}
