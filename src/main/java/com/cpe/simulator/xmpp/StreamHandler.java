package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamHandler.class);

    private final XmppConnection connection;
    private final AppConfig.XmppConfig config;
    private SaslHandler saslHandler;
    private BindHandler bindHandler;
    private volatile boolean startTlsRequested;
    private volatile boolean tlsEstablished;

    public StreamHandler(XmppConnection connection, AppConfig.XmppConfig config) {
        this.connection = connection;
        this.config = config;
    }

    public void setSaslHandler(SaslHandler saslHandler) {
        this.saslHandler = saslHandler;
    }

    public void setBindHandler(BindHandler bindHandler) {
        this.bindHandler = bindHandler;
    }

    public void openStream() {
        connection.sendStanza(new XmppStanza.StreamOpen(null, config.getDomain(), null));
        log.debug("Stream opened for device {}", connection.getDeviceId());
    }

    public void handleStreamFeatures(XmppStanza.StreamFeatures features) {
        if (config.isStartTls() && !tlsEstablished) {
            if (!features.hasStartTls()) {
                log.error("XMPP server does not offer STARTTLS for device {}", connection.getDeviceId());
                connection.setState(XmppConnection.ConnectionState.FAILED);
            } else if (!startTlsRequested) {
                startTlsRequested = true;
                connection.setState(XmppConnection.ConnectionState.TLS_NEGOTIATING);
                connection.sendStanza(new XmppStanza.StartTls());
                log.debug("STARTTLS requested for device {}", connection.getDeviceId());
            }
            return;
        }

        if (connection.getState() == XmppConnection.ConnectionState.AUTHENTICATED) {
            if (features.hasBind() && bindHandler != null) {
                bindHandler.startBind();
            }
        } else if (!features.mechanisms().isEmpty() && saslHandler != null) {
            saslHandler.startAuth(features);
        } else if (features.hasBind() && bindHandler != null) {
            bindHandler.startBind();
        } else {
            log.warn("No actionable stream features for device {}", connection.getDeviceId());
        }
    }

    public void handleStreamOpen(XmppStanza.StreamOpen open) {
        log.debug("Server acknowledged stream open for device {}, stream id: {}",
            connection.getDeviceId(), open.id());
    }

    public void handleStartTlsProceed() {
        if (!config.isStartTls() || !startTlsRequested || tlsEstablished) {
            log.error("Unexpected STARTTLS proceed for device {}", connection.getDeviceId());
            connection.setState(XmppConnection.ConnectionState.FAILED);
            return;
        }
        connection.upgradeToTls().whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("STARTTLS handshake failed for device {}", connection.getDeviceId(), error);
                connection.setState(XmppConnection.ConnectionState.FAILED);
                return;
            }
            tlsEstablished = true;
            openStream();
        });
    }

    public void handleStartTlsFailure() {
        log.error("STARTTLS negotiation rejected for device {}", connection.getDeviceId());
        connection.setState(XmppConnection.ConnectionState.FAILED);
    }
}
