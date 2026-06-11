package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class XmppConnection {

    public enum ConnectionState {
        DISCONNECTED, CONNECTING, AUTHENTICATING, AUTHENTICATED, BINDING, BOUND, ONLINE, FAILED
    }

    private volatile Channel channel;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile String jid;
    private final String deviceId;
    private final AppConfig.XmppConfig config;
    private final InetAddress localInetAddress;
    private final Consumer<String> connectionRequestCallback;
    private final ConnReqAuthConfig connReqAuthConfig;
    private volatile Consumer<ConnectionState> onStateChangeCallback;
    private volatile Consumer<String> onFailureCallback;
    private volatile Consumer<String> onSuccessCallback;
    private volatile boolean disconnectRequested;
    private volatile String xmppPassword;

    public XmppConnection(String deviceId, AppConfig.XmppConfig config, String localAddress, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.deviceId = deviceId;
        this.config = config;
        try {
            this.localInetAddress = (localAddress != null && !localAddress.isEmpty())
                ? InetAddress.getByName(localAddress) : null;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid local-address: " + localAddress, e);
        }
        this.connectionRequestCallback = connectionRequestCallback;
        this.connReqAuthConfig = connReqAuthConfig;
    }

    public CompletableFuture<Void> connect(EventLoopGroup eventLoopGroup) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        disconnectRequested = false;
        setState(ConnectionState.CONNECTING);

        StreamHandler streamHandler = new StreamHandler(this, config);
        SaslHandler saslHandler = new SaslHandler(this, config, List.of(new PlainMechanism(), new ScramSha1Mechanism()));
        BindHandler bindHandler = new BindHandler(this, config);
        PresenceHandler presenceHandler = new PresenceHandler(this);
        PingHandler pingHandler = new PingHandler(this, config);
        ConnReqAuthHandler connReqAuthHandler = new ConnReqAuthHandler(this, connReqAuthConfig);
        IqHandler iqHandler = new IqHandler(this, connectionRequestCallback, pingHandler, connReqAuthHandler);

        streamHandler.setSaslHandler(saslHandler);
        streamHandler.setBindHandler(bindHandler);
        bindHandler.setPresenceHandler(presenceHandler);

        XmppStanzaDispatcher dispatcher = new XmppStanzaDispatcher(
            streamHandler, saslHandler, iqHandler, presenceHandler, bindHandler,
            Executors.newVirtualThreadPerTaskExecutor());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    if (config.isTls()) {
                        SslContext sslContext = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build();
                        ch.pipeline().addLast("ssl",
                            new io.netty.handler.ssl.SslHandler(sslContext.newEngine(ch.alloc(), config.getHost(), config.getPort())));
                    }
                    ch.pipeline().addLast("xmppDecoder", new XmppStreamDecoder());
                    ch.pipeline().addLast("xmppEncoder", new XmppStreamEncoder());
                    ch.pipeline().addLast("dispatcher", dispatcher);
                    channel = ch;
                }
            });

        if (localInetAddress != null) {
            bootstrap.localAddress(localInetAddress, 0);
        }

        ChannelFuture channelFuture = bootstrap.connect(config.getHost(), config.getPort());
        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                channel = channelFuture.channel();
                future.complete(null);
                streamHandler.openStream();
            } else {
                setState(ConnectionState.FAILED);
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    public void disconnect() {
        disconnectRequested = true;
        if (channel != null && channel.isActive()) {
            sendStanza(new XmppStanza.PresenceStanza(null, "unavailable", null, null));
            sendStanza(new XmppStanza.StreamClose());
            channel.close();
        }
        setState(ConnectionState.DISCONNECTED);
    }

    public void sendStanza(XmppStanza stanza) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(stanza);
        }
    }

    public boolean isOnline() {
        return state == ConnectionState.ONLINE && channel != null && channel.isActive();
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
        if (onStateChangeCallback != null) {
            onStateChangeCallback.accept(state);
        }

        if (state == ConnectionState.FAILED || (state == ConnectionState.DISCONNECTED && !disconnectRequested)) {
            if (onFailureCallback != null) {
                onFailureCallback.accept(deviceId);
            }
        } else if (state == ConnectionState.ONLINE) {
            disconnectRequested = false;
            if (onSuccessCallback != null) {
                onSuccessCallback.accept(deviceId);
            }
        }
    }

    public void onStateChange(Consumer<ConnectionState> callback) {
        this.onStateChangeCallback = callback;
    }

    public void onFailure(Consumer<String> callback) { this.onFailureCallback = callback; }
    public void onSuccess(Consumer<String> callback) { this.onSuccessCallback = callback; }

    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Channel getChannel() {
        return channel;
    }

    public AppConfig.XmppConfig getConfig() {
        return config;
    }

    public String getXmppPassword() {
        return xmppPassword;
    }

    public void setXmppPassword(String xmppPassword) {
        this.xmppPassword = xmppPassword;
    }
}
