package com.cpe.simulator.xmpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresenceHandler {

    private static final Logger log = LoggerFactory.getLogger(PresenceHandler.class);

    private final XmppConnection connection;

    public PresenceHandler(XmppConnection connection) {
        this.connection = connection;
    }

    public void sendOnline() {
        connection.sendStanza(new XmppStanza.PresenceStanza(null, null, null, null));
        connection.setState(XmppConnection.ConnectionState.ONLINE);
        log.debug("Presence online sent for device {}", connection.getDeviceId());
    }

    public void sendOffline() {
        connection.sendStanza(new XmppStanza.PresenceStanza(null, "unavailable", null, null));
        log.debug("Presence offline sent for device {}", connection.getDeviceId());
    }
}
