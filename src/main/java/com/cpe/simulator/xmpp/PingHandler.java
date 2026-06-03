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
    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> timeoutFuture;
    private volatile String lastPingId;

    public PingHandler(XmppConnection connection, AppConfig.XmppConfig config) {
        this.connection = connection;
        this.config = config;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
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

    public void sendPing() {
        lastPingId = "ping_" + UUID.randomUUID().toString().substring(0, 8);
        String pingXml = "<iq type=\"get\" id=\"" + lastPingId + "\">"
            + "<ping xmlns=\"" + XmlUtil.NS_XMPP_PING + "\"/></iq>";
        connection.sendStanza(new XmppStanza.RawXml(pingXml));

        if (scheduler != null) {
            int timeout = config.getKeepaliveTimeout();
            timeoutFuture = scheduler.schedule(() -> {
                if (connection.isOnline()) {
                    log.warn("Ping timeout for device {}, triggering reconnect", connection.getDeviceId());
                    connection.setState(XmppConnection.ConnectionState.FAILED);
                }
            }, timeout, TimeUnit.SECONDS);
        }
        log.debug("Ping sent for device {}", connection.getDeviceId());
    }

    public void handlePong(XmppStanza.IqStanza pong) {
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        log.debug("Pong received for device {}", connection.getDeviceId());
    }

    public String getLastPingId() {
        return lastPingId;
    }
}
