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
import io.netty.handler.ssl.SslHandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class XmppConnection {

    public enum ConnectionState {
        DISCONNECTED, CONNECTING, TLS_NEGOTIATING, AUTHENTICATING, AUTHENTICATED, BINDING, BOUND, ONLINE, FAILED
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
    private volatile String xmppUsername;
    private volatile String xmppPassword;

    public XmppConnection(String deviceId, AppConfig.XmppConfig config, String localAddress, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this(deviceId, deviceId, config, localAddress, connectionRequestCallback, connReqAuthConfig);
    }

    public XmppConnection(String deviceId, String serialNumber, AppConfig.XmppConfig config, String localAddress,
                          Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.deviceId = deviceId;
        this.xmppUsername = serialNumber.toLowerCase(java.util.Locale.ROOT);
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
        SaslHandler saslHandler = new SaslHandler(this, config, List.of(
            new PlainMechanism(), new ScramSha256Mechanism(), new ScramSha1Mechanism()));
        BindHandler bindHandler = new BindHandler(this, config);
        PresenceHandler presenceHandler = new PresenceHandler(this);
        PingHandler pingHandler = new PingHandler(this, config);
        ConnReqAuthHandler connReqAuthHandler = new ConnReqAuthHandler(this, connReqAuthConfig);
        IqHandler iqHandler = new IqHandler(this, connectionRequestCallback, pingHandler, connReqAuthHandler);

        streamHandler.setSaslHandler(saslHandler);
        streamHandler.setBindHandler(bindHandler);
        bindHandler.setPresenceHandler(presenceHandler);

        XmppStanzaDispatcher dispatcher = new XmppStanzaDispatcher(
            this, streamHandler, saslHandler, iqHandler, presenceHandler, bindHandler,
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
                    if (config.isDirectTls()) {
                        ch.pipeline().addLast("ssl", createSslHandler(ch));
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
                if (config.isDirectTls()) {
                    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                    sslHandler.handshakeFuture().addListener(handshake -> {
                        if (handshake.isSuccess()) {
                            future.complete(null);
                            streamHandler.openStream();
                        } else {
                            setState(ConnectionState.FAILED);
                            future.completeExceptionally(handshake.cause());
                        }
                    });
                } else {
                    future.complete(null);
                    streamHandler.openStream();
                }
            } else {
                setState(ConnectionState.FAILED);
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    public CompletableFuture<Void> upgradeToTls() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Channel ch = channel;
        if (ch == null || !ch.isActive()) {
            future.completeExceptionally(new IllegalStateException("XMPP channel is not active"));
            return future;
        }

        ch.eventLoop().execute(() -> {
            try {
                if (ch.pipeline().get(SslHandler.class) != null) {
                    throw new IllegalStateException("TLS is already active");
                }
                XmppStreamDecoder decoder = ch.pipeline().get(XmppStreamDecoder.class);
                if (decoder != null) {
                    decoder.reset();
                }
                SslHandler sslHandler = createSslHandler(ch);
                ch.pipeline().addFirst("ssl", sslHandler);
                sslHandler.handshakeFuture().addListener(handshake -> {
                    if (handshake.isSuccess()) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(handshake.cause());
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private SslHandler createSslHandler(Channel ch) throws Exception {
        return XmppTlsSupport.newHandler(ch, config);
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
        if (this.state == state) {
            return;
        }
        this.state = state;

        if (state == ConnectionState.FAILED) {
            closeChannel();
        }

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

    private void closeChannel() {
        Channel ch = channel;
        if (ch != null) {
            channel = null;
            ch.close();
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

    public String getXmppUsername() {
        return xmppUsername;
    }

    public void setXmppUsername(String xmppUsername) {
        this.xmppUsername = xmppUsername;
    }

    public void setXmppPassword(String xmppPassword) {
        this.xmppPassword = xmppPassword;
    }
}
