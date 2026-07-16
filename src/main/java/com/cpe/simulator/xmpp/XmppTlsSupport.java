package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

final class XmppTlsSupport {

    private static final SslContext CLIENT_CONTEXT = buildClientContext();

    private XmppTlsSupport() {
    }

    static SslHandler newHandler(Channel channel, AppConfig.XmppConfig config) {
        SslHandler handler = CLIENT_CONTEXT.newHandler(
            channel.alloc(), config.getHost(), config.getPort());
        handler.setHandshakeTimeoutMillis(10_000);
        return handler;
    }

    private static SslContext buildClientContext() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
