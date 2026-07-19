package com.cpe.acsit.acs;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scriptable ACS test double. Speaks the CWMP HTTP dialect against the real
 * simulator: answers Inform, drives queued RPC requests down the session,
 * completes futures when RPC responses arrive, and optionally enforces
 * Basic/Digest authentication or cookie tracking.
 * <p>
 * Session model: one active CWMP session at a time (tests drive devices
 * sequentially). An Inform opens a new session; each subsequent POST either
 * completes the head-of-queue RPC future and sends the next queued RPC
 * (HTTP 200), or ends the conversation (HTTP 204).
 */
public class AcsServer {

    public enum AuthMode { NONE, BASIC, DIGEST_MD5, DIGEST_SHA256 }

    private record PendingRpc(String xml, CompletableFuture<CwmpMessage> future) {
    }

    private final int requestedPort;
    private final ConcurrentLinkedQueue<PendingRpc> rpcQueue = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<CwmpMessage> informSink = new LinkedBlockingQueue<>();
    private final BlockingQueue<CwmpMessage> transferCompleteSink = new LinkedBlockingQueue<>();
    private final List<CwmpMessage> allMessages = new CopyOnWriteArrayList<>();
    private final List<String> observedCookies = new CopyOnWriteArrayList<>();

    private volatile AuthMode authMode = AuthMode.NONE;
    private volatile String authUser = "admin";
    private volatile String authPassword = "admin";
    private volatile DigestVerifier digestVerifier;
    private volatile boolean cookieEnabled;
    private volatile String sessionCookie;
    private volatile java.util.function.Consumer<CwmpMessage> informListener;
    private volatile java.util.function.Consumer<CwmpMessage> messageListener;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private int port;

    public AcsServer() {
        this(0);
    }

    public AcsServer(int port) {
        this.requestedPort = port;
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpRequestDecoder());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                        ch.pipeline().addLast(new HttpResponseEncoder());
                        ch.pipeline().addLast(new AcsHttpHandler());
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

    public String getUrl() {
        return "http://127.0.0.1:" + port + "/";
    }

    // ---------------- configuration ----------------

    public void useBasicAuth(String user, String password) {
        this.authUser = user;
        this.authPassword = password;
        this.authMode = AuthMode.BASIC;
    }

    public void useDigestAuth(String user, String password, String algorithm) {
        this.authUser = user;
        this.authPassword = password;
        this.digestVerifier = DigestVerifier.create(algorithm);
        this.authMode = algorithm.toUpperCase(java.util.Locale.ROOT).contains("SHA")
                ? AuthMode.DIGEST_SHA256 : AuthMode.DIGEST_MD5;
    }

    public void noAuth() {
        this.authMode = AuthMode.NONE;
    }

    /** Responds with Set-Cookie on InformResponse and records Cookie headers seen. */
    public void enableCookies(boolean enabled) {
        this.cookieEnabled = enabled;
    }

    /** Registers a listener invoked on every Inform (standalone monitoring). */
    public void onInform(java.util.function.Consumer<CwmpMessage> listener) {
        this.informListener = listener;
    }

    /** Registers a listener invoked on every parsed CPE message (standalone monitoring). */
    public void onMessage(java.util.function.Consumer<CwmpMessage> listener) {
        this.messageListener = listener;
    }

    public List<String> getObservedCookies() {
        return observedCookies;
    }

    // ---------------- scripting API ----------------

    /** Queues an RPC; the future completes with the CPE's parsed response (or Fault). */
    public CompletableFuture<CwmpMessage> queueRpc(String rpcXml) {
        PendingRpc pending = new PendingRpc(rpcXml, new CompletableFuture<>());
        rpcQueue.offer(pending);
        return pending.future();
    }

    public CompletableFuture<Map<String, String>> getParameterValues(String... paths) {
        return queueRpc(CwmpRpc.getParameterValues(CwmpRpc.nextId(), paths))
                .thenApply(CwmpMessage::parameterValues);
    }

    /** Raw variant: the future completes with the response even when it is a Fault. */
    public CompletableFuture<CwmpMessage> getParameterValuesRaw(String... paths) {
        return queueRpc(CwmpRpc.getParameterValues(CwmpRpc.nextId(), paths));
    }

    /** @return future of the raw response; check isFault()/intField("Status") */
    public CompletableFuture<CwmpMessage> setParameterValues(Map<String, String> params) {
        return queueRpc(CwmpRpc.setParameterValues(CwmpRpc.nextId(), params, "acs-it-key"));
    }

    public CompletableFuture<CwmpMessage> awaitInform(Duration timeout) {
        return pollAsync(informSink, timeout, "Inform");
    }

    public CompletableFuture<CwmpMessage> awaitTransferComplete(Duration timeout) {
        return pollAsync(transferCompleteSink, timeout, "TransferComplete");
    }

    /** Waits for an Inform carrying the given event code, skipping others (with overall timeout). */
    public CwmpMessage awaitInformWithEvent(String eventCodePrefix, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0) {
                throw new AssertionError("Timed out waiting for Inform with event " + eventCodePrefix
                        + "; seen so far: " + summarizeEvents());
            }
            CwmpMessage inform = informSink.poll(remainingMillis, TimeUnit.MILLISECONDS);
            if (inform == null) {
                continue;
            }
            for (String code : inform.eventCodes()) {
                if (code.startsWith(eventCodePrefix)) {
                    return inform;
                }
            }
        }
    }

    public List<CwmpMessage> getAllMessages() {
        return allMessages;
    }

    public long countInforms() {
        return allMessages.stream().filter(m -> "Inform".equals(m.method())).count();
    }

    private String summarizeEvents() {
        StringBuilder sb = new StringBuilder("[");
        for (CwmpMessage m : allMessages) {
            if ("Inform".equals(m.method())) {
                sb.append(m.eventCodes()).append(' ');
            }
        }
        return sb.append(']').toString();
    }

    private static CompletableFuture<CwmpMessage> pollAsync(BlockingQueue<CwmpMessage> queue,
                                                            Duration timeout, String what) {
        CompletableFuture<CwmpMessage> future = new CompletableFuture<>();
        Thread.ofVirtual().name("acs-await-" + what.toLowerCase()).start(() -> {
            try {
                CwmpMessage message = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (message != null) {
                    future.complete(message);
                } else {
                    future.completeExceptionally(
                            new AssertionError("Timed out waiting for " + what));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Ends any in-flight RPC expectations (called between test phases). */
    public void failPendingRpcs(String reason) {
        PendingRpc pending;
        while ((pending = rpcQueue.poll()) != null) {
            pending.future().completeExceptionally(new AssertionError(reason));
        }
    }

    // ---------------- HTTP handling ----------------

    private class AcsHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!checkAuth(ctx, request)) {
                return;
            }
            String cookie = request.headers().get(HttpHeaderNames.COOKIE);
            if (cookie != null) {
                observedCookies.add(cookie);
            }

            String body = ByteBufUtil.getBytes(request.content()) != null
                    ? request.content().toString(StandardCharsets.UTF_8) : "";
            CwmpMessage message = CwmpMessage.parse(body);
            allMessages.add(message);
            if (messageListener != null) {
                messageListener.accept(message);
            }

            switch (message.method()) {
                case "Inform" -> {
                    informSink.offer(message);
                    if (informListener != null) {
                        informListener.accept(message);
                    }
                    FullHttpResponse response = respond(HttpResponseStatus.OK,
                            CwmpRpc.informResponse(message.cwmpId(), 1));
                    if (cookieEnabled) {
                        sessionCookie = "ACSSESSION=" + System.nanoTime();
                        response.headers().set(HttpHeaderNames.SET_COOKIE, sessionCookie);
                    }
                    writeAndClose(ctx, response);
                }
                case "TransferComplete" -> {
                    transferCompleteSink.offer(message);
                    writeAndClose(ctx, respond(HttpResponseStatus.OK,
                            CwmpRpc.transferCompleteResponse(message.cwmpId())));
                }
                case "" -> driveNext(ctx); // empty POST: CPE is ready for the next RPC
                default -> {
                    // RPC response (or Fault) for the head-of-queue request
                    PendingRpc pending = rpcQueue.poll();
                    if (pending != null) {
                        pending.future().complete(message);
                    }
                    driveNext(ctx);
                }
            }
        }

        private void driveNext(ChannelHandlerContext ctx) {
            PendingRpc next = rpcQueue.peek();
            if (next != null) {
                writeAndClose(ctx, respond(HttpResponseStatus.OK, next.xml()));
            } else {
                writeAndClose(ctx, new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT));
            }
        }

        private boolean checkAuth(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (authMode == AuthMode.NONE) {
                return true;
            }
            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            boolean ok = switch (authMode) {
                case BASIC -> {
                    String expected = "Basic " + Base64.getEncoder().encodeToString(
                            (authUser + ":" + authPassword).getBytes(StandardCharsets.UTF_8));
                    yield expected.equals(authorization);
                }
                case DIGEST_MD5, DIGEST_SHA256 -> digestVerifier.verify(
                        authorization, authUser, authPassword, request.method().name());
                default -> true;
            };
            if (ok) {
                return true;
            }
            FullHttpResponse challenge = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
            String headerValue = authMode == AuthMode.BASIC
                    ? "Basic realm=\"ACS\"" : digestVerifier.challenge();
            challenge.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, headerValue);
            challenge.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            writeAndClose(ctx, challenge);
            return false;
        }

        private FullHttpResponse respond(HttpResponseStatus status, String body) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/xml; charset=utf-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            return response;
        }

        private void writeAndClose(ChannelHandlerContext ctx, FullHttpResponse response) {
            // The simulator opens a fresh request per POST; closing keeps things simple
            if (response.headers().get(HttpHeaderNames.CONTENT_LENGTH) == null) {
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH,
                        response.content() != null ? response.content().readableBytes() : 0);
            }
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    /** Handle for tests that want the raw AtomicReference of the latest session cookie. */
    public AtomicReference<String> sessionCookieRef() {
        return new AtomicReference<>(sessionCookie);
    }
}
