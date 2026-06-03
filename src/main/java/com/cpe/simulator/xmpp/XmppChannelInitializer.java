package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLEngine;
import java.util.function.Consumer;

public class XmppChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final AppConfig.XmppConfig config;
    private final Consumer<Channel> channelCallback;

    public XmppChannelInitializer(AppConfig.XmppConfig config, Consumer<Channel> channelCallback) {
        this.config = config;
        this.channelCallback = channelCallback;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        if (config.isTls()) {
            SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
            SSLEngine sslEngine = sslContext.newEngine(ch.alloc(), config.getHost(), config.getPort());
            pipeline.addLast("ssl", new io.netty.handler.ssl.SslHandler(sslEngine));
        }

        pipeline.addLast("xmppDecoder", new XmppStreamDecoder());
        pipeline.addLast("xmppEncoder", new XmppStreamEncoder());

        if (channelCallback != null) {
            channelCallback.accept(ch);
        }
    }
}
