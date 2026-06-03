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
}
