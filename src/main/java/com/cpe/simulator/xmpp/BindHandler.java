package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.SimLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BindHandler {

    private static final Logger log = LoggerFactory.getLogger(BindHandler.class);
    private static final Pattern JID_PATTERN = Pattern.compile("<jid>([^<]+)</jid>");

    private final XmppConnection connection;
    private final AppConfig.XmppConfig config;
    private PresenceHandler presenceHandler;

    public BindHandler(XmppConnection connection, AppConfig.XmppConfig config) {
        this.connection = connection;
        this.config = config;
    }

    public void setPresenceHandler(PresenceHandler presenceHandler) {
        this.presenceHandler = presenceHandler;
    }

    public void startBind() {
        connection.setState(XmppConnection.ConnectionState.BINDING);
        String bindId = "bind_" + UUID.randomUUID().toString().substring(0, 8);
        connection.sendStanza(new XmppStanza.BindStanza(bindId, config.getResource(), null));
        SimLogger.device(connection.getDeviceId(),
            "XMPP resource bind sent: iqId={} resource={} snapshot={}",
            bindId, config.getResource(), connection.diagnosticSnapshot());
        log.debug("Bind request sent for device {}", connection.getDeviceId());
    }

    public void handleBindResult(XmppStanza.IqStanza iq) {
        if ("result".equals(iq.type())) {
            String jid = null;
            if (iq.rawXml() != null) {
                Matcher matcher = JID_PATTERN.matcher(iq.rawXml());
                if (matcher.find()) {
                    jid = matcher.group(1);
                }
            }
            connection.setJid(jid);
            connection.setState(XmppConnection.ConnectionState.BOUND);
            SimLogger.device(connection.getDeviceId(),
                "XMPP resource bind succeeded: iqId={} jid={}", iq.id(), jid);
            log.debug("Bind succeeded for device {}, JID: {}", connection.getDeviceId(), jid);

            if (presenceHandler != null) {
                presenceHandler.sendOnline();
            }
        } else if ("error".equals(iq.type())) {
            log.error("Bind failed for device {}, iqId={}, response={}, snapshot={}",
                connection.getDeviceId(), iq.id(), summarizeXml(iq.rawXml()), connection.diagnosticSnapshot());
            connection.setState(XmppConnection.ConnectionState.FAILED);
        }
    }

    private static String summarizeXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return "-";
        }
        String singleLine = xml.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 512 ? singleLine : singleLine.substring(0, 512) + "...";
    }
}
