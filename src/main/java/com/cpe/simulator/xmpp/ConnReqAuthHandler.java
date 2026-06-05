package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import com.cpe.simulator.cwmp.DigestAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ConnReqAuthHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnReqAuthHandler.class);
    private static final long NONCE_EXPIRY_MS = 5 * 60 * 1000;

    private final XmppConnection connection;
    private final ConnReqAuthConfig authConfig;

    private volatile String pendingNonce;
    private volatile String pendingRealm;
    private volatile String pendingAlgorithm;
    private volatile long nonceTimestamp;

    public ConnReqAuthHandler(XmppConnection connection, ConnReqAuthConfig authConfig) {
        this.connection = connection;
        this.authConfig = authConfig;
    }

    public void handle(XmppStanza.IqStanza iq, Consumer<String> callback) {
        if (!authConfig.enabled()) {
            sendResult(iq);
            if (callback != null) {
                callback.accept(connection.getDeviceId());
            }
            return;
        }

        Map<String, String> attrs = parseConnReqAttrs(iq.rawXml());
        String username = attrs.get("username");
        String response = attrs.get("response");

        if (username == null && response == null) {
            sendChallenge(iq);
            return;
        }

        if (pendingNonce == null) {
            sendChallenge(iq);
            return;
        }

        String nonce = attrs.get("nonce");
        if (!pendingNonce.equals(nonce)) {
            sendAuthError(iq);
            return;
        }

        if (isNonceExpired()) {
            sendChallenge(iq);
            return;
        }

        String realm = attrs.getOrDefault("realm", pendingRealm);
        String algorithm = attrs.getOrDefault("algorithm", pendingAlgorithm);
        String qop = attrs.get("qop");
        String nc = attrs.get("nc");
        String cnonce = attrs.get("cnonce");
        String digestUri = iq.to() != null ? iq.to() : connection.getJid();

        String expectedResponse = DigestAuth.calculateDigestResponse(
            algorithm, authConfig.username(), authConfig.password(),
            realm, nonce, digestUri, "GET", qop, nc, cnonce);

        clearNonce();

        if (expectedResponse.equals(response)) {
            log.info("XMPP ConnectionRequest auth succeeded for device {}", connection.getDeviceId());
            sendResult(iq);
            if (callback != null) {
                callback.accept(connection.getDeviceId());
            }
        } else {
            log.warn("XMPP ConnectionRequest auth failed for device {}", connection.getDeviceId());
            sendAuthError(iq);
        }
    }

    private String generateNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isNonceExpired() {
        return System.currentTimeMillis() - nonceTimestamp > NONCE_EXPIRY_MS;
    }

    private void clearNonce() {
        pendingNonce = null;
        pendingRealm = null;
        pendingAlgorithm = null;
        nonceTimestamp = 0;
    }

    Map<String, String> parseConnReqAttrs(String rawXml) {
        Map<String, String> attrs = new HashMap<>();
        if (rawXml == null) return attrs;

        int connReqIdx = rawXml.indexOf("<connReq");
        if (connReqIdx < 0) return attrs;

        int connReqEnd = rawXml.indexOf('>', connReqIdx);
        if (connReqEnd < 0) return attrs;

        String connReqTag = rawXml.substring(connReqIdx, connReqEnd + 1);

        String[] attrNames = {"username", "response", "nonce", "realm", "algorithm", "qop", "nc", "cnonce"};
        for (String attrName : attrNames) {
            String value = XmppStreamDecoder.extractAttribute(connReqTag, attrName);
            if (value != null) {
                attrs.put(attrName, value);
            }
        }
        return attrs;
    }

    private void sendChallenge(XmppStanza.IqStanza iq) {
        String nonce = generateNonce();
        pendingNonce = nonce;
        pendingRealm = connection.getConfig().getDomain();
        pendingAlgorithm = authConfig.algorithm();
        nonceTimestamp = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("<iq type=\"error\" id=\"").append(XmlUtil.escapeXml(iq.id())).append("\"");
        if (iq.from() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(iq.from())).append("\"");
        }
        sb.append(">");
        sb.append("<connReq xmlns=\"").append(XmlUtil.NS_CWMP_XMPP_CONN_REQ).append("\">");
        sb.append("<challenge algorithm=\"").append(XmlUtil.escapeXml(authConfig.algorithm())).append("\"");
        sb.append(" nonce=\"").append(XmlUtil.escapeXml(nonce)).append("\"");
        sb.append(" realm=\"").append(XmlUtil.escapeXml(connection.getConfig().getDomain())).append("\"/>");
        sb.append("</connReq>");
        sb.append("<error type=\"modify\">");
        sb.append("<not-authorized xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>");
        sb.append("</error>");
        sb.append("</iq>");

        connection.sendStanza(new XmppStanza.RawXml(sb.toString()));
        log.debug("Challenge sent for device {}, nonce={}", connection.getDeviceId(), nonce);
    }

    private void sendResult(XmppStanza.IqStanza iq) {
        String resultXml = "<iq type=\"result\" id=\"" + XmlUtil.escapeXml(iq.id()) + "\"";
        if (iq.from() != null) {
            resultXml += " to=\"" + XmlUtil.escapeXml(iq.from()) + "\"";
        }
        resultXml += "/>";
        connection.sendStanza(new XmppStanza.RawXml(resultXml));
    }

    private void sendAuthError(XmppStanza.IqStanza iq) {
        StringBuilder sb = new StringBuilder();
        sb.append("<iq type=\"error\" id=\"").append(XmlUtil.escapeXml(iq.id())).append("\"");
        if (iq.from() != null) {
            sb.append(" to=\"").append(XmlUtil.escapeXml(iq.from())).append("\"");
        }
        sb.append(">");
        sb.append("<error type=\"modify\">");
        sb.append("<not-authorized xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>");
        sb.append("</error>");
        sb.append("</iq>");

        connection.sendStanza(new XmppStanza.RawXml(sb.toString()));
    }

    void forceNonceExpiry() {
        nonceTimestamp = System.currentTimeMillis() - NONCE_EXPIRY_MS - 1000;
    }
}