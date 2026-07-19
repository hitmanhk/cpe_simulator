package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import com.cpe.simulator.common.SimLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

public class ConnReqAuthHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnReqAuthHandler.class);

    private final XmppConnection connection;
    private final ConnReqAuthConfig authConfig;

    public ConnReqAuthHandler(XmppConnection connection, ConnReqAuthConfig authConfig) {
        this.connection = connection;
        this.authConfig = authConfig;
    }

    public void handle(XmppStanza.IqStanza iq, Consumer<String> callback) {
        String[] credentials = authConfig.credentialsFor(connection.getDeviceId());
        SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_RECEIVED",
            "iqId=" + value(iq.id())
                + " from=" + value(iq.from())
                + " to=" + value(iq.to())
                + " localJid=" + value(connection.getJid())
                + " authEnabled=" + (credentials != null)
                + " attempt=" + connection.getCurrentAttempt());
        if (credentials == null) {
            sendResult(iq);
            if (callback != null) {
                callback.accept(connection.getDeviceId());
                SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_DISPATCHED",
                    "iqId=" + value(iq.id()) + " auth=disabled");
            } else {
                SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_NOT_DISPATCHED",
                    "iqId=" + value(iq.id()) + " reason=no_callback");
            }
            return;
        }

        Map<String, String> childElements = parseConnReqChildElements(iq.rawXml());
        String username = childElements.get("username");
        String password = childElements.get("password");

        if (username != null && username.equals(credentials[0])
            && password != null && password.equals(credentials[1])) {
            log.info("XMPP ConnectionRequest auth succeeded for device {}", connection.getDeviceId());
            sendResult(iq);
            if (callback != null) {
                callback.accept(connection.getDeviceId());
                SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_DISPATCHED",
                    "iqId=" + value(iq.id()) + " auth=success");
            } else {
                SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_NOT_DISPATCHED",
                    "iqId=" + value(iq.id()) + " reason=no_callback");
            }
        } else {
            log.warn("XMPP ConnectionRequest auth failed for device {}", connection.getDeviceId());
            SimLogger.xmppEvent(connection.getDeviceId(), "CONNECTION_REQUEST_REJECTED",
                "iqId=" + value(iq.id()) + " reason=authentication_failed");
            sendAuthError(iq, iq.rawXml());
        }
    }

    Map<String, String> parseConnReqChildElements(String rawXml) {
        Map<String, String> elements = new java.util.HashMap<>();
        if (rawXml == null) return elements;

        int startIdx = rawXml.indexOf("<connectionRequest");
        if (startIdx < 0) return elements;

        int closeIdx = rawXml.indexOf("</connectionRequest>", startIdx);
        if (closeIdx < 0) {
            int selfCloseIdx = rawXml.indexOf("/>", startIdx);
            if (selfCloseIdx < 0) return elements;
            closeIdx = selfCloseIdx;
        }

        String innerContent = extractInnerContent(rawXml, startIdx, closeIdx);

        String[] childNames = {"username", "password"};
        for (String childName : childNames) {
            String value = extractChildElementText(innerContent, childName);
            if (value != null) {
                elements.put(childName, value);
            }
        }
        return elements;
    }

    private String extractInnerContent(String rawXml, int startIdx, int closeIdx) {
        int tagEnd = rawXml.indexOf('>', startIdx);
        if (tagEnd < 0 || tagEnd >= closeIdx) return "";
        return rawXml.substring(tagEnd + 1, closeIdx);
    }

    private String extractChildElementText(String content, String elementName) {
        String openTag = "<" + elementName + ">";
        String closeTag = "</" + elementName + ">";
        int start = content.indexOf(openTag);
        if (start < 0) return null;
        int textStart = start + openTag.length();
        int end = content.indexOf(closeTag, textStart);
        if (end < 0) return null;
        return XmlUtil.unescapeXml(content.substring(textStart, end));
    }

    private void sendResult(XmppStanza.IqStanza iq) {
        String resultXml = "<iq type=\"result\" id=\"" + XmlUtil.escapeXml(iq.id()) + "\"";
        if (iq.from() != null) {
            resultXml += " to=\"" + XmlUtil.escapeXml(iq.from()) + "\"";
        }
        resultXml += "/>";
        connection.sendStanza(new XmppStanza.RawXml(resultXml));
    }

    private void sendAuthError(XmppStanza.IqStanza iq, String connReqXml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<iq type=\"error\" id=\"").append(XmlUtil.escapeXml(iq.id())).append("\"");
        if (iq.from() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(iq.from())).append("\"");
        }
        sb.append(">");
        sb.append(extractConnReqBody(connReqXml));
        sb.append("<error type=\"cancel\">");
        sb.append("<not-authorized xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>");
        sb.append("</error>");
        sb.append("</iq>");

        connection.sendStanza(new XmppStanza.RawXml(sb.toString()));
    }

    private String extractConnReqBody(String rawXml) {
        if (rawXml == null) return "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>";

        int startIdx = rawXml.indexOf("<connectionRequest");
        if (startIdx < 0) return "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>";

        int endIdx = rawXml.indexOf("</connectionRequest>", startIdx);
        if (endIdx >= 0) {
            return rawXml.substring(startIdx, endIdx + "</connectionRequest>".length());
        }

        int selfCloseIdx = rawXml.indexOf("/>", startIdx);
        if (selfCloseIdx >= 0) {
            return rawXml.substring(startIdx, selfCloseIdx + 2);
        }

        return "<connectionRequest xmlns=\"" + XmlUtil.NS_CWMP_XMPP_CONN_REQ + "\"/>";
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
