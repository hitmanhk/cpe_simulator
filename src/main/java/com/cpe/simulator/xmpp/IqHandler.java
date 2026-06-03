package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class IqHandler {

    private static final Logger log = LoggerFactory.getLogger(IqHandler.class);

    private final XmppConnection connection;
    private final Consumer<String> connectionRequestCallback;
    private final PingHandler pingHandler;

    public IqHandler(XmppConnection connection, Consumer<String> connectionRequestCallback, PingHandler pingHandler) {
        this.connection = connection;
        this.connectionRequestCallback = connectionRequestCallback;
        this.pingHandler = pingHandler;
    }

    public void handleIq(XmppStanza.IqStanza iq) {
        if (iq.childNamespace() == null) {
            if ("result".equals(iq.type())) {
                if (pingHandler != null && iq.id() != null && iq.id().equals(pingHandler.getLastPingId())) {
                    pingHandler.handlePong(iq);
                }
                return;
            }
            return;
        }

        switch (iq.childNamespace()) {
            case XmlUtil.NS_CWMP_XMPP_CONN_REQ -> handleConnectionRequest(iq);
            case XmlUtil.NS_XMPP_PING -> {
                if ("get".equals(iq.type()) && pingHandler != null) {
                    pingHandler.handlePing(iq);
                }
            }
            default -> sendError(iq);
        }
    }

    private void handleConnectionRequest(XmppStanza.IqStanza iq) {
        String resultXml = "<iq type=\"result\" id=\"" + XmlUtil.escapeXml(iq.id()) + "\"";
        if (iq.from() != null) {
            resultXml += " to=\"" + XmlUtil.escapeXml(iq.from()) + "\"";
        }
        resultXml += "/>";
        connection.sendStanza(new XmppStanza.RawXml(resultXml));

        log.info("Connection request received for device {}", connection.getDeviceId());
        if (connectionRequestCallback != null) {
            connectionRequestCallback.accept(connection.getDeviceId());
        }
    }

    private void sendError(XmppStanza.IqStanza iq) {
        String errorXml = "<iq type=\"error\" id=\"" + XmlUtil.escapeXml(iq.id()) + "\"";
        if (iq.from() != null) {
            errorXml += " to=\"" + XmlUtil.escapeXml(iq.from()) + "\"";
        }
        errorXml += "><error type=\"cancel\">"
            + "<service-unavailable xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>"
            + "</error></iq>";
        connection.sendStanza(new XmppStanza.RawXml(errorXml));
        log.debug("Service unavailable error sent for unknown IQ namespace: {}", iq.childNamespace());
    }
}
