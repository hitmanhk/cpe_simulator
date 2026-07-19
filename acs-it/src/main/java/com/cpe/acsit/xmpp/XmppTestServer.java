package com.cpe.acsit.xmpp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded XMPP server test double implementing exactly the handshake the
 * simulator speaks: stream open, optional DIRECT/STARTTLS TLS, SASL
 * PLAIN/SCRAM-SHA-1/SCRAM-SHA-256 (full server-side verification), resource
 * bind, presence, XEP-0199 ping, and the TR-069 connection-request IQ.
 * <p>
 * The server is scripted by the tests: it records every stanza per session,
 * lets tests await ONLINE sessions, send connection requests, and kill
 * connections to exercise the simulator's reconnect backoff.
 */
public class XmppTestServer {

    public enum TlsMode { NONE, DIRECT, STARTTLS }

    private static final String NS_CLIENT = "jabber:client";
    private static final String NS_STREAM = "http://etherx.jabber.org/streams";
    private static final String NS_TLS = "urn:ietf:params:xml:ns:xmpp-tls";
    private static final String NS_SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    private static final String NS_BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    private static final String NS_PING = "urn:xmpp:ping";
    private static final String NS_CONN_REQ = "urn:broadband-forum-org:cwmp:xmppConnReq-1-0";

    private final String domain;
    private final int requestedPort;
    private volatile String expectedPassword = "secret";
    private volatile TlsMode tlsMode = TlsMode.NONE;
    private volatile List<String> mechanisms = List.of("PLAIN", "SCRAM-SHA-1", "SCRAM-SHA-256");

    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final BlockingQueue<ClientSession> onlineSink = new LinkedBlockingQueue<>();
    private final AtomicInteger sessionIds = new AtomicInteger();
    private final Map<String, CompletableFuture<StanzaView>> pendingIqs = new ConcurrentHashMap<>();
    private final AtomicInteger iqIds = new AtomicInteger();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private SslContext sslContext;
    private int port;

    public XmppTestServer(String domain) {
        this(domain, 0);
    }

    public XmppTestServer(String domain, int port) {
        this.domain = domain;
        this.requestedPort = port;
    }

    public XmppTestServer tlsMode(TlsMode mode) {
        this.tlsMode = mode;
        return this;
    }

    public XmppTestServer expectedPassword(String password) {
        this.expectedPassword = password;
        return this;
    }

    public XmppTestServer mechanisms(List<String> mechanisms) {
        this.mechanisms = mechanisms;
        return this;
    }

    public void start() throws Exception {
        TlsCertUtil.SelfSignedCert cert = TlsCertUtil.generate(domain);
        sslContext = SslContextBuilder.forServer(cert.privateKey(), cert.certificate()).build();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (tlsMode == TlsMode.DIRECT) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                        }
                        ch.pipeline().addLast(new StanzaDecoder());
                        ch.pipeline().addLast(new SessionHandler());
                    }
                });
        ChannelFuture future = bootstrap.bind("127.0.0.1", requestedPort).awaitUninterruptibly();
        serverChannel = future.channel();
        port = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().awaitUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
        }
    }

    public int getPort() {
        return port;
    }

    public List<ClientSession> sessions() {
        return sessions;
    }

    /** Waits for the next session to reach ONLINE. */
    public ClientSession awaitOnline(Duration timeout) throws InterruptedException {
        ClientSession session = onlineSink.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (session == null) {
            throw new AssertionError("Timed out waiting for XMPP session ONLINE (tlsMode="
                    + tlsMode + ", sessions seen: " + sessions.size() + ")");
        }
        return session;
    }

    public long onlineCount() {
        return sessions.stream().filter(s -> s.state() == ClientSession.State.ONLINE).count();
    }

    /**
     * Sends a TR-069 connection request IQ to an ONLINE session; the future
     * completes with the CPE's IQ result/error stanza.
     */
    public CompletableFuture<StanzaView> sendConnectionRequest(ClientSession session,
                                                               String username, String password) {
        String id = "connreq-" + iqIds.incrementAndGet();
        CompletableFuture<StanzaView> future = new CompletableFuture<>();
        pendingIqs.put(id, future);
        String iq = "<iq type=\"set\" id=\"" + id + "\" from=\"acs@" + domain + "\">"
                + "<connectionRequest xmlns=\"" + NS_CONN_REQ + "\">"
                + "<username>" + username + "</username>"
                + "<password>" + password + "</password>"
                + "</connectionRequest></iq>";
        writeTo(session, iq);
        return future;
    }

    /** Forcibly closes a session's TCP connection (reconnect testing). */
    public void killSession(ClientSession session) {
        Channel channel = sessionChannels.get(session.id());
        if (channel != null) {
            channel.close();
        }
    }

    /** Closes every active connection. */
    public void killAllSessions() {
        sessionChannels.values().forEach(Channel::close);
    }

    private final Map<Integer, Channel> sessionChannels = new ConcurrentHashMap<>();

    private void writeTo(ClientSession session, String stanza) {
        Channel channel = sessionChannels.get(session.id());
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(
                    stanza.getBytes(StandardCharsets.UTF_8)));
        } else {
            throw new IllegalStateException("Session channel not active: " + session.id());
        }
    }

    // ---------------- pipeline ----------------

    private static class StanzaDecoder extends ByteToMessageDecoder {
        private final StanzaTokenizer tokenizer = new StanzaTokenizer();

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);
            out.addAll(tokenizer.feed(bytes));
        }
    }

    private class SessionHandler extends SimpleChannelInboundHandler<String> {

        private enum Phase { EXPECT_STREAM_OPEN, EXPECT_TLS_OR_AUTH, EXPECT_AUTH,
            EXPECT_SCRAM_FINAL, EXPECT_BIND, EXPECT_PRESENCE, ONLINE }

        private final ClientSession session = new ClientSession(sessionIds.incrementAndGet());
        private Phase phase = Phase.EXPECT_STREAM_OPEN;
        private boolean tlsUpgraded;
        private ScramServer scram;
        private String streamId;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            sessions.add(session);
            sessionChannels.put(session.id(), ctx.channel());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            session.state(ClientSession.State.CLOSED);
            sessionChannels.remove(session.id());
            super.channelInactive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String rawStanza) throws Exception {
            session.record(rawStanza);
            StanzaView stanza = new StanzaView(rawStanza);

            if (stanza.isStreamOpen()) {
                handleStreamOpen(ctx);
                return;
            }

            switch (phase) {
                case EXPECT_TLS_OR_AUTH -> {
                    if ("starttls".equals(stanza.elementName())) {
                        send(ctx, "<proceed xmlns=\"" + NS_TLS + "\"/>");
                        session.state(ClientSession.State.TLS_NEGOTIATING);
                        SslHandler sslHandler = sslContext.newHandler(ctx.channel().alloc());
                        ctx.pipeline().addFirst(sslHandler);
                        tlsUpgraded = true;
                        phase = Phase.EXPECT_STREAM_OPEN;
                    } else {
                        send(ctx, "<failure xmlns=\"" + NS_TLS + "\"/></stream:stream>");
                        ctx.close();
                    }
                }
                case EXPECT_AUTH -> handleAuth(ctx, stanza);
                case EXPECT_SCRAM_FINAL -> handleScramFinal(ctx, stanza);
                case EXPECT_BIND -> handleBind(ctx, stanza);
                case EXPECT_PRESENCE -> {
                    if ("presence".equals(stanza.elementName())) {
                        session.state(ClientSession.State.ONLINE);
                        phase = Phase.ONLINE;
                        onlineSink.offer(session);
                    }
                }
                case ONLINE -> handleOnlineStanza(ctx, stanza);
                default -> {
                    // stray stanza in an unexpected phase; ignore to stay robust
                }
            }
        }

        private void handleStreamOpen(ChannelHandlerContext ctx) {
            streamId = "stream-" + session.id() + "-" + System.nanoTime();
            send(ctx, "<?xml version=\"1.0\"?>"
                    + "<stream:stream xmlns=\"" + NS_CLIENT + "\" xmlns:stream=\"" + NS_STREAM + "\""
                    + " id=\"" + streamId + "\" from=\"" + domain + "\" version=\"1.0\">");
            if (tlsMode == TlsMode.STARTTLS && !tlsUpgraded) {
                send(ctx, "<stream:features><starttls xmlns=\"" + NS_TLS + "\">"
                        + "<required/></starttls></stream:features>");
                phase = Phase.EXPECT_TLS_OR_AUTH;
            } else if (session.state() == ClientSession.State.AUTHENTICATED) {
                send(ctx, "<stream:features><bind xmlns=\"" + NS_BIND + "\"/></stream:features>");
                phase = Phase.EXPECT_BIND;
            } else {
                send(ctx, mechanismsFeatures());
                phase = Phase.EXPECT_AUTH;
            }
        }

        private String mechanismsFeatures() {
            StringBuilder sb = new StringBuilder("<stream:features><mechanisms xmlns=\"")
                    .append(NS_SASL).append("\">");
            for (String mechanism : mechanisms) {
                sb.append("<mechanism>").append(mechanism).append("</mechanism>");
            }
            return sb.append("</mechanisms></stream:features>").toString();
        }

        private void handleAuth(ChannelHandlerContext ctx, StanzaView stanza) {
            if (!"auth".equals(stanza.elementName())) {
                return;
            }
            String mechanism = stanza.attr("mechanism");
            session.state(ClientSession.State.AUTHENTICATING);
            if ("PLAIN".equals(mechanism)) {
                handlePlain(ctx, stanza.rootText());
            } else if ("SCRAM-SHA-1".equals(mechanism) || "SCRAM-SHA-256".equals(mechanism)) {
                try {
                    scram = new ScramServer(mechanism);
                    String clientFirst = new String(Base64.getDecoder().decode(stanza.rootText()),
                            StandardCharsets.UTF_8);
                    String serverFirst = scram.handleClientFirst(clientFirst);
                    session.username(scram.username());
                    send(ctx, "<challenge xmlns=\"" + NS_SASL + "\">"
                            + Base64.getEncoder().encodeToString(serverFirst.getBytes(StandardCharsets.UTF_8))
                            + "</challenge>");
                    phase = Phase.EXPECT_SCRAM_FINAL;
                } catch (ScramServer.AuthException e) {
                    sendAuthFailure(ctx);
                } catch (IllegalArgumentException e) {
                    sendAuthFailure(ctx);
                }
            } else {
                send(ctx, "<failure xmlns=\"" + NS_SASL + "\">"
                        + "<invalid-mechanism/></failure>");
            }
        }

        private void handlePlain(ChannelHandlerContext ctx, String base64Payload) {
            try {
                String decoded = new String(Base64.getDecoder().decode(base64Payload),
                        StandardCharsets.UTF_8);
                String[] parts = decoded.split("\0", -1);
                // [authzid], authcid, password
                String authcid = parts.length >= 2 ? parts[parts.length - 2] : "";
                String password = parts.length >= 1 ? parts[parts.length - 1] : "";
                session.username(authcid);
                if (expectedPassword.equals(password)) {
                    session.mechanism("PLAIN");
                    sendAuthSuccess(ctx, null);
                } else {
                    sendAuthFailure(ctx);
                }
            } catch (Exception e) {
                sendAuthFailure(ctx);
            }
        }

        private void handleScramFinal(ChannelHandlerContext ctx, StanzaView stanza) {
            if (!"response".equals(stanza.elementName()) || scram == null) {
                return;
            }
            try {
                String clientFinal = new String(Base64.getDecoder().decode(stanza.rootText()),
                        StandardCharsets.UTF_8);
                String serverFinal = scram.handleClientFinal(clientFinal, expectedPassword);
                session.mechanism(scram.mechanism());
                sendAuthSuccess(ctx, Base64.getEncoder()
                        .encodeToString(serverFinal.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                sendAuthFailure(ctx);
                phase = Phase.EXPECT_AUTH;
            }
        }

        private void sendAuthSuccess(ChannelHandlerContext ctx, String serverFinalBase64) {
            session.state(ClientSession.State.AUTHENTICATED);
            if (serverFinalBase64 != null) {
                send(ctx, "<success xmlns=\"" + NS_SASL + "\">" + serverFinalBase64 + "</success>");
            } else {
                send(ctx, "<success xmlns=\"" + NS_SASL + "\"/>");
            }
            phase = Phase.EXPECT_STREAM_OPEN; // stream restart
        }

        private void sendAuthFailure(ChannelHandlerContext ctx) {
            send(ctx, "<failure xmlns=\"" + NS_SASL + "\"><not-authorized/></failure>");
            phase = Phase.EXPECT_AUTH;
        }

        private void handleBind(ChannelHandlerContext ctx, StanzaView stanza) {
            if (!"iq".equals(stanza.elementName()) || !"set".equals(stanza.attr("type"))) {
                return;
            }
            session.state(ClientSession.State.BINDING);
            String resource = stanza.childText("resource");
            if (resource == null || resource.isEmpty()) {
                resource = "auto-" + session.id();
            }
            session.resource(resource);
            String jid = session.username() + "@" + domain + "/" + resource;
            session.jid(jid);
            send(ctx, "<iq type=\"result\" id=\"" + stanza.attr("id") + "\">"
                    + "<bind xmlns=\"" + NS_BIND + "\"><jid>" + jid + "</jid></bind></iq>");
            phase = Phase.EXPECT_PRESENCE;
        }

        private void handleOnlineStanza(ChannelHandlerContext ctx, StanzaView stanza) {
            switch (stanza.elementName()) {
                case "iq" -> {
                    String type = stanza.attr("type");
                    String id = stanza.attr("id");
                    if ("get".equals(type) && NS_PING.equals(stanza.firstChildNamespace())) {
                        session.incrementPingCount();
                        send(ctx, "<iq type=\"result\" id=\"" + id + "\"/>");
                    } else if ("result".equals(type) || "error".equals(type)) {
                        CompletableFuture<StanzaView> pending = id != null ? pendingIqs.remove(id) : null;
                        if (pending != null) {
                            pending.complete(stanza);
                        }
                    } else if (id != null) {
                        // generic ack for any other IQ so the client never stalls
                        send(ctx, "<iq type=\"result\" id=\"" + id + "\"/>");
                    }
                }
                case "presence" -> {
                    if ("unavailable".equals(stanza.attr("type"))) {
                        session.state(ClientSession.State.CLOSED);
                        ctx.close();
                    }
                }
                case "stream:close" -> {
                    session.state(ClientSession.State.CLOSED);
                    ctx.close();
                }
                default -> {
                    // messages and other stanzas are recorded but need no reply
                }
            }
        }

        private void send(ChannelHandlerContext ctx, String xml) {
            ctx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(
                    xml.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            session.state(ClientSession.State.CLOSED);
            ctx.close();
        }
    }
}
