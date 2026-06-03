package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CwmpServerHandler extends ChannelInboundHandlerAdapter {

    private static final String CONN_REQ_PREFIX = "/connReq/";

    private final AppConfig config;
    private final CwmpServer.ConnectionRequestHandler requestHandler;

    public CwmpServerHandler(AppConfig config, CwmpServer.ConnectionRequestHandler handler) {
        this.config = config;
        this.requestHandler = handler;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest request)) {
            return;
        }

        try {
            if (config.isConnReqAuthEnabled() && !isAuthorized(request)) {
                sendResponse(ctx, HttpResponseStatus.UNAUTHORIZED, "Unauthorized");
                return;
            }

            HttpMethod method = request.method();
            String uri = request.uri();

            if (method != HttpMethod.GET || !uri.startsWith(CONN_REQ_PREFIX)) {
                sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
                return;
            }

            String deviceId = uri.substring(CONN_REQ_PREFIX.length());
            if (deviceId.isEmpty()) {
                sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
                return;
            }

            CwmpServer.ConnectionRequestResult result = requestHandler.handleConnectionRequest(deviceId);

            HttpResponseStatus status = switch (result.statusCode()) {
                case 200 -> HttpResponseStatus.OK;
                case 404 -> HttpResponseStatus.NOT_FOUND;
                case 503 -> HttpResponseStatus.SERVICE_UNAVAILABLE;
                default -> HttpResponseStatus.valueOf(result.statusCode());
            };

            sendResponse(ctx, status, result.message());
        } finally {
            request.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private boolean isAuthorized(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
            int colonIdx = decoded.indexOf(':');
            if (colonIdx < 0) return false;

            String user = decoded.substring(0, colonIdx);
            String pass = decoded.substring(colonIdx + 1);
            return user.equals(config.getConnReqUser()) && pass.equals(config.getConnReqPassword());
        } catch (Exception e) {
            return false;
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(content)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);

        if (status == HttpResponseStatus.UNAUTHORIZED && config.isConnReqAuthEnabled()) {
            response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"CPE Connection Request\"");
        }

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
