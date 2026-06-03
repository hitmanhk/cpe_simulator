package com.cpe.simulator;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.SimulationEngine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PublicModeIntegrationTest {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private SimulationEngine engine;
    private int acsPort;
    private int connReqPort;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile boolean informReceived = false;
    private volatile boolean sessionCompleted = false;

    private static final String INFORM_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
            <soap-env:Header>
            <cwmp:ID soap-env:mustUnderstand="1">acs-001</cwmp:ID>
            </soap-env:Header>
            <soap-env:Body>
            <cwmp:InformResponse>
            <MaxEnvelopes>0</MaxEnvelopes>
            </cwmp:InformResponse>
            </soap-env:Body>
            </soap-env:Envelope>""";

    @BeforeEach
    void setUp() throws Exception {
        acsPort = findFreePort();
        connReqPort = findFreePort();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stopForced();
        }
        stopMockAcs();
    }

    @Test
    @Timeout(30)
    void testPublicModeEndToEnd() throws Exception {
        startMockAcs();

        AppConfig config = new AppConfig();
        config.setMode("public");
        config.setCount(1);
        config.setAcsUrl("http://127.0.0.1:" + acsPort + "/");
        config.setAcsUser("admin");
        config.setAcsPassword("admin");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(connReqPort);
        config.setMaxSessions(10);
        config.setInformInterval(3600);
        config.setInformJitter(0.0);
        config.setSessionTimeout(30);
        config.setConnectBatch(1000);
        config.setConnectBatchDelay(0);

        engine = new SimulationEngine(config);
        engine.start();

        assertTrue(engine.isRunning());

        long deadline = System.currentTimeMillis() + 15_000;
        while (!informReceived && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(informReceived, "ACS should have received Inform");

        Thread.sleep(2000);

        assertTrue(requestCount.get() >= 2, "Should have received multiple POST requests, got: " + requestCount.get());

        var registry = engine.getDeviceRegistry();
        assertNotNull(registry);
        assertEquals(1, registry.getDeviceCount());

        var deviceCtx = registry.getAllDevices().iterator().next();
        CpeDevice device = (CpeDevice) deviceCtx;

        long informDeadline = System.currentTimeMillis() + 10_000;
        while (device.getInformCount() == 0 && System.currentTimeMillis() < informDeadline) {
            Thread.sleep(200);
        }
        assertTrue(device.getInformCount() > 0, "Device should have completed at least one inform");
    }

    @Test
    @Timeout(30)
    void testDeviceStateTransitions() throws Exception {
        startMockAcs();

        AppConfig config = new AppConfig();
        config.setMode("public");
        config.setCount(1);
        config.setAcsUrl("http://127.0.0.1:" + acsPort + "/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(connReqPort);
        config.setMaxSessions(10);
        config.setInformInterval(3600);
        config.setInformJitter(0.0);
        config.setSessionTimeout(30);
        config.setConnectBatch(1000);
        config.setConnectBatchDelay(0);

        engine = new SimulationEngine(config);
        engine.start();

        var registry = engine.getDeviceRegistry();
        var deviceCtx = registry.getAllDevices().iterator().next();
        CpeDevice device = (CpeDevice) deviceCtx;

        long deadline = System.currentTimeMillis() + 15_000;
        while (device.getInformCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        assertTrue(device.getInformCount() > 0);
        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
    }

    private void startMockAcs() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new HttpRequestDecoder());
                        ch.pipeline().addLast(new HttpObjectAggregator(65536));
                        ch.pipeline().addLast(new HttpResponseEncoder());
                        ch.pipeline().addLast(new MockAcsHandler());
                    }
                });

        ChannelFuture future = bootstrap.bind("127.0.0.1", acsPort).sync();
        serverChannel = future.channel();
    }

    private void stopMockAcs() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    private class MockAcsHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof FullHttpRequest request)) {
                return;
            }

            try {
                int reqNum = requestCount.incrementAndGet();

                if (reqNum == 1) {
                    informReceived = true;
                    sendResponse(ctx, HttpResponseStatus.OK, INFORM_RESPONSE);
                } else {
                    sendNoContent(ctx);
                }
            } finally {
                request.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(content));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/xml; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendNoContent(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT, Unpooled.EMPTY_BUFFER);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
