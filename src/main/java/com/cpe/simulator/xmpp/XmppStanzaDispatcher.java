package com.cpe.simulator.xmpp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class XmppStanzaDispatcher extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(XmppStanzaDispatcher.class);

    private final StreamHandler streamHandler;
    private final SaslHandler saslHandler;
    private final IqHandler iqHandler;
    private final PresenceHandler presenceHandler;
    private final BindHandler bindHandler;
    private final ExecutorService virtualThreadExecutor;

    public XmppStanzaDispatcher(StreamHandler streamHandler,
                                 SaslHandler saslHandler,
                                 IqHandler iqHandler,
                                 PresenceHandler presenceHandler,
                                 BindHandler bindHandler,
                                 ExecutorService virtualThreadExecutor) {
        this.streamHandler = streamHandler;
        this.saslHandler = saslHandler;
        this.iqHandler = iqHandler;
        this.presenceHandler = presenceHandler;
        this.bindHandler = bindHandler;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof XmppStanza stanza)) {
            ctx.fireChannelRead(msg);
            return;
        }

        virtualThreadExecutor.execute(() -> dispatch(stanza));
    }

    private void dispatch(XmppStanza stanza) {
        try {
            switch (stanza) {
                case XmppStanza.StreamOpen open -> streamHandler.handleStreamOpen(open);
                case XmppStanza.StreamFeatures features -> streamHandler.handleStreamFeatures(features);
                case XmppStanza.AuthSuccess success -> saslHandler.handleAuthResult(success);
                case XmppStanza.AuthFailure failure -> saslHandler.handleAuthResult(failure);
                case XmppStanza.IqStanza iq -> handleIqStanza(iq);
                case XmppStanza.PresenceStanza presence -> handlePresence(presence);
                default -> log.debug("Unhandled stanza type: {}", stanza.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error processing stanza: {}", stanza.getClass().getSimpleName(), e);
        }
    }

    private void handleIqStanza(XmppStanza.IqStanza iq) {
        if ("result".equals(iq.type()) && iq.childNamespace() == null) {
            String rawXml = iq.rawXml();
            if (rawXml != null && rawXml.contains("bind")) {
                bindHandler.handleBindResult(iq);
                return;
            }
        }
        if ("result".equals(iq.type()) || "error".equals(iq.type())) {
            if (iq.childNamespace() == null) {
                bindHandler.handleBindResult(iq);
                return;
            }
        }
        iqHandler.handleIq(iq);
    }

    private void handlePresence(XmppStanza.PresenceStanza presence) {
        log.debug("Received presence stanza: type={}", presence.type());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("XMPP channel exception", cause);
        ctx.close();
    }
}
