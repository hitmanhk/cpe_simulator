package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

public class CwmpServer {

    public interface ConnectionRequestHandler {
        ConnectionRequestResult handleConnectionRequest(String deviceId);
    }

    public record ConnectionRequestResult(boolean success, int statusCode, String message) {}

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final int port;
    private final String host;
    private final AppConfig config;
    private final ConnectionRequestHandler requestHandler;

    public CwmpServer(AppConfig config, ConnectionRequestHandler handler) {
        this.config = config;
        this.port = config.getConnReqPort();
        this.host = config.getConnReqHost();
        this.requestHandler = handler;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpRequestDecoder());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new HttpResponseEncoder());
                        pipeline.addLast(new CwmpServerHandler(config, requestHandler));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        ChannelFuture future = bootstrap.bind(host, port).sync();
        serverChannel = future.channel();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public int getPort() {
        return port;
    }
}
