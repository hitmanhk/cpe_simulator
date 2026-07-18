package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.SimLogger;
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
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong attemptSequence = new AtomicLong();
    private volatile long currentAttempt;
    private volatile long attemptStartedNanos;
    private volatile long stateChangedNanos = System.nanoTime();
    private volatile long lastInboundNanos;
    private volatile long lastOutboundNanos;
    private volatile String lastInboundType = "-";
    private volatile String lastOutboundType = "-";
    private volatile EventLoopGroup eventLoopGroup;
    private volatile ScheduledFuture<?> handshakeTimeoutFuture;
    private final AtomicLong handshakeTimerSequence = new AtomicLong();
    private volatile PingHandler activePingHandler;

    public XmppConnection(String deviceId, AppConfig.XmppConfig config, String bindHost, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this(deviceId, deviceId, config, bindHost, connectionRequestCallback, connReqAuthConfig);
    }

    public XmppConnection(String deviceId, String serialNumber, AppConfig.XmppConfig config, String bindHost,
                          Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.deviceId = deviceId;
        this.xmppUsername = serialNumber.toLowerCase(java.util.Locale.ROOT);
        this.config = config;
        try {
            this.localInetAddress = (bindHost != null && !bindHost.isEmpty())
                ? InetAddress.getByName(bindHost) : null;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid conn-req-host: " + bindHost, e);
        }
        this.connectionRequestCallback = connectionRequestCallback;
        this.connReqAuthConfig = connReqAuthConfig;
    }

    public CompletableFuture<Void> connect(EventLoopGroup eventLoopGroup) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        stopKeepalive();
        cancelHandshakeTimeout();
        this.eventLoopGroup = eventLoopGroup;
        long attempt = attemptSequence.incrementAndGet();
        currentAttempt = attempt;
        attemptStartedNanos = System.nanoTime();
        stateChangedNanos = attemptStartedNanos;
        lastInboundNanos = 0;
        lastOutboundNanos = 0;
        lastInboundType = "-";
        lastOutboundType = "-";
        disconnectRequested = false;
        setState(ConnectionState.CONNECTING);
        SimLogger.device(deviceId,
            "XMPP connect started: attempt={} username={} domain={} resource={} target={}:{} requestedLocal={} tlsMode={}",
            attempt, safeValue(xmppUsername), safeValue(config.getDomain()), safeValue(config.getResource()),
            safeValue(config.getHost()), config.getPort(),
            localInetAddress != null ? localInetAddress.getHostAddress() : "auto", config.getTlsMode());

        StreamHandler streamHandler = new StreamHandler(this, config);
        SaslHandler saslHandler = new SaslHandler(this, config, List.of(
            new PlainMechanism(), new ScramSha256Mechanism(), new ScramSha1Mechanism()));
        BindHandler bindHandler = new BindHandler(this, config);
        PresenceHandler presenceHandler = new PresenceHandler(this);
        PingHandler pingHandler = new PingHandler(this, config);
        activePingHandler = pingHandler;
        ConnReqAuthHandler connReqAuthHandler = new ConnReqAuthHandler(this, connReqAuthConfig);
        IqHandler iqHandler = new IqHandler(this, connectionRequestCallback, pingHandler, connReqAuthHandler);

        streamHandler.setSaslHandler(saslHandler);
        streamHandler.setBindHandler(bindHandler);
        bindHandler.setPresenceHandler(presenceHandler);

        XmppStanzaDispatcher dispatcher = new XmppStanzaDispatcher(
            this, streamHandler, saslHandler, iqHandler, presenceHandler, bindHandler,
            Executors.newVirtualThreadPerTaskExecutor(), attempt);

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
                if (!isCurrentAttemptActive(attempt)) {
                    channelFuture.channel().close();
                    future.completeExceptionally(new IllegalStateException(
                        "XMPP connect attempt " + attempt + " is no longer active"));
                    return;
                }
                channel = channelFuture.channel();
                pingHandler.setScheduler(channel.eventLoop());
                SimLogger.xmppEvent(deviceId, "TCP_CONNECTED",
                    "attempt=" + attempt
                        + " connectMs=" + elapsedMillis(attemptStartedNanos)
                        + " local=" + localAddress()
                        + " remote=" + remoteAddress()
                        + " tlsMode=" + config.getTlsMode());
                if (config.isDirectTls()) {
                    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                    sslHandler.handshakeFuture().addListener(handshake -> {
                        if (!isCurrentAttemptActive(attempt)) {
                            channelFuture.channel().close();
                            future.completeExceptionally(new IllegalStateException(
                                "XMPP TLS attempt " + attempt + " is no longer active"));
                            return;
                        }
                        if (handshake.isSuccess()) {
                            SimLogger.device(deviceId,
                                "XMPP direct TLS established: attempt={} elapsedMs={} local={} remote={}",
                                attempt, elapsedMillis(attemptStartedNanos), localAddress(), remoteAddress());
                            future.complete(null);
                            streamHandler.openStream();
                        } else {
                            SimLogger.xmppEvent(deviceId, "TLS_FAILED",
                                "attempt=" + attempt + " state=" + state
                                    + " error=" + errorSummary(handshake.cause())
                                    + " local=" + localAddress() + " remote=" + remoteAddress());
                            failCurrentAttempt(attempt);
                            future.completeExceptionally(handshake.cause());
                        }
                    });
                } else {
                    future.complete(null);
                    streamHandler.openStream();
                }
            } else {
                SimLogger.xmppEvent(deviceId, "TCP_CONNECT_FAILED",
                    "attempt=" + attempt + " connectMs=" + elapsedMillis(attemptStartedNanos)
                        + " target=" + config.getHost() + ":" + config.getPort()
                        + " requestedLocal="
                        + (localInetAddress != null ? localInetAddress.getHostAddress() : "auto")
                        + " error=" + errorSummary(f.cause()));
                failCurrentAttempt(attempt);
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
        Channel ch = channel;
        if (ch != null && ch.isActive()) {
            recordOutbound(stanza);
            ch.writeAndFlush(stanza).addListener(write -> {
                if (!write.isSuccess()) {
                    SimLogger.xmppEvent(deviceId, "STANZA_WRITE_FAILED",
                        "attempt=" + currentAttempt + " stanza=" + stanzaType(stanza)
                            + " state=" + state + " error=" + errorSummary(write.cause())
                            + " local=" + address(ch.localAddress())
                            + " remote=" + address(ch.remoteAddress()));
                }
            });
        } else {
            SimLogger.warn("[{}] XMPP stanza dropped: attempt={} stanza={} state={} channelActive=false",
                deviceId, currentAttempt, stanzaType(stanza), state);
        }
    }

    public boolean isOnline() {
        return state == ConnectionState.ONLINE && channel != null && channel.isActive();
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        transitionState(state, -1, -1);
    }

    private boolean transitionState(ConnectionState state, long requiredAttempt, long requiredTimer) {
        ConnectionState previousState;
        long previousStateAgeMs;
        long now = System.nanoTime();
        synchronized (this) {
            if (requiredAttempt >= 0 && currentAttempt != requiredAttempt) {
                return false;
            }
            if (requiredAttempt >= 0
                && (this.state == ConnectionState.DISCONNECTED || this.state == ConnectionState.FAILED)) {
                return false;
            }
            if (requiredTimer >= 0 && handshakeTimerSequence.get() != requiredTimer) {
                return false;
            }
            previousState = this.state;
            if (requiredTimer >= 0 && !isHandshakeState(previousState)) {
                return false;
            }
            if (previousState == state) {
                return false;
            }
            previousStateAgeMs = nanosToMillis(now - stateChangedNanos);
            this.state = state;
            stateChangedNanos = now;
        }
        SimLogger.connectionState(deviceId, "XMPP", previousState, state,
            "attempt=" + currentAttempt
                + " previousStateAgeMs=" + previousStateAgeMs
                + " attemptElapsedMs=" + getAttemptElapsedMillis()
                + " channelActive=" + hasActiveChannel()
                + " local=" + localAddress()
                + " remote=" + remoteAddress());

        if (isHandshakeState(state)) {
            scheduleHandshakeTimeout(currentAttempt, state);
        } else {
            cancelHandshakeTimeout();
        }

        if (state == ConnectionState.ONLINE) {
            startKeepalive();
        } else if (state == ConnectionState.FAILED || state == ConnectionState.DISCONNECTED) {
            stopKeepalive();
        }

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
        return true;
    }

    private void scheduleHandshakeTimeout(long attempt, ConnectionState stage) {
        int timeoutSeconds = config.getHandshakeTimeout();
        EventLoopGroup group = eventLoopGroup;
        if (timeoutSeconds <= 0 || group == null || group.isShuttingDown()) {
            return;
        }

        synchronized (this) {
            if (currentAttempt != attempt || state != stage) {
                return;
            }
            long timer = handshakeTimerSequence.incrementAndGet();
            ScheduledFuture<?> previous = handshakeTimeoutFuture;
            if (previous != null) {
                previous.cancel(false);
            }
            handshakeTimeoutFuture = group.schedule(() -> {
                String snapshot = diagnosticSnapshot();
                if (transitionState(ConnectionState.FAILED, attempt, timer)) {
                    SimLogger.xmppEvent(deviceId, "HANDSHAKE_TIMEOUT",
                        "stage=" + stage + " timeoutSeconds=" + timeoutSeconds + " " + snapshot);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    private synchronized void cancelHandshakeTimeout() {
        handshakeTimerSequence.incrementAndGet();
        ScheduledFuture<?> timeout = handshakeTimeoutFuture;
        handshakeTimeoutFuture = null;
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    private void startKeepalive() {
        PingHandler pingHandler = activePingHandler;
        if (pingHandler != null) {
            pingHandler.start();
        }
    }

    private void stopKeepalive() {
        PingHandler pingHandler = activePingHandler;
        if (pingHandler != null) {
            pingHandler.stop();
        }
    }

    void failCurrentAttempt(long attempt) {
        transitionState(ConnectionState.FAILED, attempt, -1);
    }

    private synchronized boolean isCurrentAttemptActive(long attempt) {
        return currentAttempt == attempt
            && state != ConnectionState.FAILED
            && state != ConnectionState.DISCONNECTED;
    }

    private static boolean isHandshakeState(ConnectionState state) {
        return state == ConnectionState.CONNECTING
            || state == ConnectionState.TLS_NEGOTIATING
            || state == ConnectionState.AUTHENTICATING
            || state == ConnectionState.AUTHENTICATED
            || state == ConnectionState.BINDING
            || state == ConnectionState.BOUND;
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

    void recordInbound(XmppStanza stanza) {
        lastInboundNanos = System.nanoTime();
        lastInboundType = stanzaType(stanza);
        SimLogger.device(deviceId,
            "XMPP stanza received: attempt={} stanza={} state={} local={} remote={}",
            currentAttempt, lastInboundType, state, localAddress(), remoteAddress());
    }

    private void recordOutbound(XmppStanza stanza) {
        lastOutboundNanos = System.nanoTime();
        lastOutboundType = stanzaType(stanza);
        SimLogger.device(deviceId,
            "XMPP stanza sent: attempt={} stanza={} state={} local={} remote={}",
            currentAttempt, lastOutboundType, state, localAddress(), remoteAddress());
    }

    public boolean hasActiveChannel() {
        Channel ch = channel;
        return ch != null && ch.isActive();
    }

    public long getCurrentAttempt() {
        return currentAttempt;
    }

    public long getStateAgeMillis() {
        return nanosToMillis(System.nanoTime() - stateChangedNanos);
    }

    public long getAttemptElapsedMillis() {
        long started = attemptStartedNanos;
        return started == 0 ? 0 : elapsedMillis(started);
    }

    public String diagnosticSnapshot() {
        long now = System.nanoTime();
        return "device=" + deviceId
            + ",attempt=" + currentAttempt
            + ",state=" + state
            + ",stateAgeMs=" + nanosToMillis(now - stateChangedNanos)
            + ",attemptElapsedMs=" + getAttemptElapsedMillis()
            + ",channelActive=" + hasActiveChannel()
            + ",local=" + localAddress()
            + ",remote=" + remoteAddress()
            + ",jid=" + safeValue(jid)
            + ",lastInbound=" + lastInboundType + "/" + ageMillis(now, lastInboundNanos) + "ms"
            + ",lastOutbound=" + lastOutboundType + "/" + ageMillis(now, lastOutboundNanos) + "ms";
    }

    private String localAddress() {
        Channel ch = channel;
        return ch != null ? address(ch.localAddress())
            : localInetAddress != null ? localInetAddress.getHostAddress() + ":auto" : "unassigned";
    }

    private String remoteAddress() {
        Channel ch = channel;
        return ch != null ? address(ch.remoteAddress()) : config.getHost() + ":" + config.getPort();
    }

    private static String address(SocketAddress address) {
        return address != null ? address.toString() : "unassigned";
    }

    private static String stanzaType(XmppStanza stanza) {
        return stanza != null ? stanza.getClass().getSimpleName() : "null";
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String errorSummary(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ":" + message.replaceAll("\\s+", " "));
    }

    private static long elapsedMillis(long startNanos) {
        return nanosToMillis(System.nanoTime() - startNanos);
    }

    private static long ageMillis(long now, long timestamp) {
        return timestamp == 0 ? -1 : nanosToMillis(now - timestamp);
    }

    private static long nanosToMillis(long nanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(nanos));
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
