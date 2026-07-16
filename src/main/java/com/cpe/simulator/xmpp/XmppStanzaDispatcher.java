package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class XmppStanzaDispatcher extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(XmppStanzaDispatcher.class);

    private final XmppConnection connection;
    private final StreamHandler streamHandler;
    private final SaslHandler saslHandler;
    private final IqHandler iqHandler;
    private final PresenceHandler presenceHandler;
    private final BindHandler bindHandler;
    private final ExecutorService virtualThreadExecutor;

    public XmppStanzaDispatcher(XmppConnection connection,
                                 StreamHandler streamHandler,
                                 SaslHandler saslHandler,
                                 IqHandler iqHandler,
                                 PresenceHandler presenceHandler,
                                 BindHandler bindHandler,
                                 ExecutorService virtualThreadExecutor) {
        this.connection = connection;
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
                case XmppStanza.AuthChallenge challenge -> saslHandler.handleChallenge(challenge);
                case XmppStanza.StartTlsProceed ignored -> streamHandler.handleStartTlsProceed();
                case XmppStanza.StartTlsFailure ignored -> streamHandler.handleStartTlsFailure();
                case XmppStanza.IqStanza iq -> handleIqStanza(iq);
                case XmppStanza.PresenceStanza presence -> handlePresence(presence);
                default -> log.debug("Unhandled stanza type: {}", stanza.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error processing stanza: {}", stanza.getClass().getSimpleName(), e);
        }
    }

    private void handleIqStanza(XmppStanza.IqStanza iq) {
        if ("result".equals(iq.type()) || "error".equals(iq.type())) {
            String ns = iq.childNamespace();
            if (XmlUtil.NS_XMPP_BIND.equals(ns)) {
                bindHandler.handleBindResult(iq);
                return;
            }
            if (ns == null && iq.rawXml() != null && iq.rawXml().contains("bind")) {
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
        log.error("XMPP channel exception for device {}", connection.getDeviceId(), cause);
        connection.setState(XmppConnection.ConnectionState.FAILED);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("XMPP channel inactive for device {}", connection.getDeviceId());
        // Don't overwrite FAILED state — channel close is already handled by setState(FAILED)
        if (connection.getState() != XmppConnection.ConnectionState.FAILED) {
            connection.setState(XmppConnection.ConnectionState.DISCONNECTED);
        }
        super.channelInactive(ctx);
    }
}
