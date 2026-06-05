package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class XmppEngine {

    private static final Logger log = LoggerFactory.getLogger(XmppEngine.class);

    private final AppConfig appConfig;
    private final Consumer<String> connectionRequestCallback;
    private final ConnReqAuthConfig connReqAuthConfig;
    private final List<XmppConnection> connections = new CopyOnWriteArrayList<>();
    private final NioEventLoopGroup eventLoopGroup;
    private final ScheduledExecutorService scheduler;
    private final ReconnectPolicy reconnectPolicy;
    private volatile boolean running = false;

    public XmppEngine(AppConfig appConfig, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.appConfig = appConfig;
        this.connectionRequestCallback = connectionRequestCallback;
        this.connReqAuthConfig = connReqAuthConfig;
        this.eventLoopGroup = new NioEventLoopGroup(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.reconnectPolicy = new ReconnectPolicy();
    }

    public XmppConnection createConnection(String deviceId) {
        return new XmppConnection(deviceId, appConfig.getXmpp(), appConfig.getLocalAddress(), connectionRequestCallback, connReqAuthConfig);
    }

    public void start(List<XmppConnection> connections) {
        this.connections.addAll(connections);
        running = true;

        int batchSize = appConfig.getConnectBatch();
        int batchDelay = appConfig.getConnectBatchDelay();

        scheduler.execute(() -> {
            for (int i = 0; i < connections.size(); i += batchSize) {
                int end = Math.min(i + batchSize, connections.size());
                List<XmppConnection> batch = connections.subList(i, end);

                for (XmppConnection conn : batch) {
                    try {
                        conn.connect(eventLoopGroup);
                    } catch (Exception e) {
                        log.error("Failed to connect device {}", conn.getDeviceId(), e);
                    }
                }

                if (end < connections.size()) {
                    try {
                        Thread.sleep(batchDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            log.info("All {} XMPP connections initiated", connections.size());
        });

        scheduler.scheduleWithFixedDelay(this::healthCheck, 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
        for (XmppConnection conn : connections) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                log.error("Error disconnecting device {}", conn.getDeviceId(), e);
            }
        }
        eventLoopGroup.shutdownGracefully();
        log.info("XMPP engine stopped");
    }

    public void healthCheck() {
        if (!running) return;

        for (XmppConnection conn : connections) {
            if (conn.getState() == XmppConnection.ConnectionState.FAILED
                || conn.getState() == XmppConnection.ConnectionState.DISCONNECTED) {
                log.debug("Scheduling reconnect for device {}", conn.getDeviceId());
                reconnectPolicy.scheduleReconnect(() -> {
                    if (running) {
                        try {
                            conn.setState(XmppConnection.ConnectionState.DISCONNECTED);
                            conn.connect(eventLoopGroup);
                        } catch (Exception e) {
                            log.error("Reconnect failed for device {}", conn.getDeviceId(), e);
                        }
                    }
                }, scheduler);
            }
        }
    }

    public int getOnlineCount() {
        int count = 0;
        for (XmppConnection conn : connections) {
            if (conn.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public List<XmppConnection> getConnections() {
        return connections;
    }

    public boolean isRunning() {
        return running;
    }
}
