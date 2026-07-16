package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class XmppEngine {

    private static final Logger log = LoggerFactory.getLogger(XmppEngine.class);

    private final AppConfig appConfig;
    private final Consumer<String> connectionRequestCallback;
    private final ConnReqAuthConfig connReqAuthConfig;
    private final List<XmppConnection> connections = new CopyOnWriteArrayList<>();
    private final NioEventLoopGroup eventLoopGroup;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private ReconnectScheduler reconnectScheduler;

    public XmppEngine(AppConfig appConfig, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.appConfig = appConfig;
        this.connectionRequestCallback = connectionRequestCallback;
        this.connReqAuthConfig = connReqAuthConfig;
        this.eventLoopGroup = new NioEventLoopGroup(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public XmppConnection createConnection(String deviceId) {
        return new XmppConnection(deviceId, appConfig.getXmpp(), appConfig.getLocalAddress(), connectionRequestCallback, connReqAuthConfig);
    }

    public XmppConnection createConnection(String deviceId, String serialNumber) {
        return new XmppConnection(deviceId, serialNumber, appConfig.getXmpp(), appConfig.getLocalAddress(), connectionRequestCallback, connReqAuthConfig);
    }

    public void start(List<XmppConnection> connections) {
        this.connections.addAll(connections);
        running = true;

        this.reconnectScheduler = new ReconnectScheduler(scheduler, this::getConnection, eventLoopGroup);

        int batchSize = appConfig.getConnectBatch();
        int batchDelay = appConfig.getConnectBatchDelay();

        scheduler.execute(() -> {
            for (int i = 0; i < connections.size(); i += batchSize) {
                int end = Math.min(i + batchSize, connections.size());
                List<XmppConnection> batch = connections.subList(i, end);

                for (XmppConnection conn : batch) {
                    try {
                        registerCallbacks(conn);
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
    }

    public void stop() {
        running = false;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
        }
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

    public void addConnection(XmppConnection conn) {
        connections.add(conn);
        registerCallbacks(conn);
        try {
            conn.connect(eventLoopGroup);
        } catch (Exception e) {
            log.error("Failed to connect device {}", conn.getDeviceId(), e);
        }
    }

    private void registerCallbacks(XmppConnection conn) {
        conn.onFailure(reconnectScheduler::onFailure);
        conn.onSuccess(reconnectScheduler::onSuccess);
    }

    private XmppConnection getConnection(String deviceId) {
        return connections.stream()
            .filter(c -> c.getDeviceId().equals(deviceId))
            .findFirst().orElse(null);
    }
}
