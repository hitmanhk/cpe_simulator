package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PingHandler {

    private static final Logger log = LoggerFactory.getLogger(PingHandler.class);

    private final XmppConnection connection;
    private final AppConfig.XmppConfig config;
    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> keepaliveFuture;
    private volatile ScheduledFuture<?> timeoutFuture;
    private volatile String lastPingId;
    private volatile long lastPingAttempt;

    public PingHandler(XmppConnection connection, AppConfig.XmppConfig config) {
        this.connection = connection;
        this.config = config;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void start() {
        if (keepaliveFuture != null && !keepaliveFuture.isDone()) {
            return;
        }
        if (scheduler == null) {
            log.warn("Cannot start XMPP keepalive without scheduler for device {}", connection.getDeviceId());
            return;
        }
        int interval = config.getKeepaliveInterval();
        if (interval <= 0) {
            log.info("XMPP keepalive disabled for device {}", connection.getDeviceId());
            return;
        }
        keepaliveFuture = scheduler.scheduleWithFixedDelay(
            this::sendPing, interval, interval, TimeUnit.SECONDS);
        log.debug("XMPP keepalive started for device {}, interval={}s timeout={}s",
            connection.getDeviceId(), interval, config.getKeepaliveTimeout());
    }

    public synchronized void stop() {
        if (keepaliveFuture != null) {
            keepaliveFuture.cancel(false);
            keepaliveFuture = null;
        }
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        lastPingId = null;
        lastPingAttempt = 0;
    }

    public void handlePing(XmppStanza.IqStanza ping) {
        String resultXml = "<iq type=\"result\" id=\"" + XmlUtil.escapeXml(ping.id()) + "\"";
        if (ping.from() != null) {
            resultXml += " to=\"" + XmlUtil.escapeXml(ping.from()) + "\"";
        }
        resultXml += "/>";
        connection.sendStanza(new XmppStanza.RawXml(resultXml));
        log.debug("Ping response sent for device {}", connection.getDeviceId());
    }

    public synchronized void sendPing() {
        if (!connection.isOnline()) {
            return;
        }
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            log.debug("Previous XMPP ping still pending for device {}, pingId={}",
                connection.getDeviceId(), lastPingId);
            return;
        }
        lastPingId = "ping_" + UUID.randomUUID().toString().substring(0, 8);
        String pingId = lastPingId;
        lastPingAttempt = connection.getCurrentAttempt();
        long pingAttempt = lastPingAttempt;
        String pingXml = "<iq type=\"get\" id=\"" + lastPingId + "\">"
            + "<ping xmlns=\"" + XmlUtil.NS_XMPP_PING + "\"/></iq>";
        connection.sendStanza(new XmppStanza.RawXml(pingXml));

        if (scheduler != null) {
            int timeout = config.getKeepaliveTimeout();
            if (timeout > 0) {
                timeoutFuture = scheduler.schedule(
                    () -> onPingTimeout(pingId, pingAttempt), timeout, TimeUnit.SECONDS);
            }
        }
        log.debug("Ping sent for device {}", connection.getDeviceId());
    }

    public synchronized void handlePong(XmppStanza.IqStanza pong) {
        if (pong.id() == null || !pong.id().equals(lastPingId)) {
            log.debug("Ignoring stale XMPP pong for device {}, pongId={} expected={}",
                connection.getDeviceId(), pong.id(), lastPingId);
            return;
        }
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        log.debug("Pong received for device {}", connection.getDeviceId());
    }

    public String getLastPingId() {
        return lastPingId;
    }

    private void onPingTimeout(String pingId, long pingAttempt) {
        synchronized (this) {
            if (!pingId.equals(lastPingId) || pingAttempt != lastPingAttempt) {
                return;
            }
            timeoutFuture = null;
        }
        if (connection.isOnline() && connection.getCurrentAttempt() == pingAttempt) {
            log.warn("Ping timeout for device {}, pingId={}, triggering reconnect",
                connection.getDeviceId(), pingId);
            connection.failCurrentAttempt(pingAttempt);
        }
    }
}
