package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConnectionRequestEndpoint;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class XmppEngine {

    private static final Logger log = LoggerFactory.getLogger(XmppEngine.class);
    private static final long DIAGNOSTIC_INITIAL_DELAY_SECONDS = 30;
    private static final long DIAGNOSTIC_INTERVAL_SECONDS = 60;
    private static final long STALLED_STATE_THRESHOLD_MS = 30_000;
    private static final int STALLED_SAMPLE_LIMIT = 10;

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
        return new XmppConnection(deviceId, appConfig.getXmpp(),
            ConnectionRequestEndpoint.resolveOutboundBindHost(appConfig),
            connectionRequestCallback, connReqAuthConfig);
    }

    public XmppConnection createConnection(String deviceId, String serialNumber) {
        return new XmppConnection(deviceId, serialNumber, appConfig.getXmpp(),
            ConnectionRequestEndpoint.resolveOutboundBindHost(appConfig),
            connectionRequestCallback, connReqAuthConfig);
    }

    public void start(List<XmppConnection> connections) {
        this.connections.addAll(connections);
        running = true;

        this.reconnectScheduler = new ReconnectScheduler(
            scheduler, this::getConnection, eventLoopGroup, appConfig.getXmpp());
        scheduler.scheduleWithFixedDelay(this::logConnectionDiagnostics,
            DIAGNOSTIC_INITIAL_DELAY_SECONDS, DIAGNOSTIC_INTERVAL_SECONDS, TimeUnit.SECONDS);

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

    void logConnectionDiagnostics() {
        if (!running) {
            return;
        }

        Map<XmppConnection.ConnectionState, Integer> stateCounts =
            new EnumMap<>(XmppConnection.ConnectionState.class);
        int tcpActive = 0;
        int tcpActiveButNotOnline = 0;
        List<String> stalledSamples = new java.util.ArrayList<>();

        for (XmppConnection connection : connections) {
            stateCounts.merge(connection.getState(), 1, Integer::sum);
            if (connection.hasActiveChannel()) {
                tcpActive++;
                if (!connection.isOnline()) {
                    tcpActiveButNotOnline++;
                    if (connection.getStateAgeMillis() >= STALLED_STATE_THRESHOLD_MS
                        && stalledSamples.size() < STALLED_SAMPLE_LIMIT) {
                        stalledSamples.add(connection.diagnosticSnapshot());
                    }
                }
            }
        }

        log.info("XMPP connection summary: total={} tcpActive={} online={} tcpActiveButNotOnline={} states={}",
            connections.size(), tcpActive, getOnlineCount(), tcpActiveButNotOnline, stateCounts);
        if (!stalledSamples.isEmpty()) {
            log.warn("XMPP protocol stalled: thresholdMs={} sampleCount={} samples={}",
                STALLED_STATE_THRESHOLD_MS, stalledSamples.size(), stalledSamples);
        }
    }
}
