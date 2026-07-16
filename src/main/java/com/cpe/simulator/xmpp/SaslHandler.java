package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class SaslHandler {

    private static final Logger log = LoggerFactory.getLogger(SaslHandler.class);

    private final XmppConnection connection;
    private final AppConfig.XmppConfig config;
    private final List<SaslMechanism> mechanisms;
    private SaslMechanism selectedMechanism;
    private String authUsername;
    private String authPassword;

    public SaslHandler(XmppConnection connection, AppConfig.XmppConfig config, List<SaslMechanism> mechanisms) {
        this.connection = connection;
        this.config = config;
        this.mechanisms = mechanisms;
    }

    public void startAuth(XmppStanza.StreamFeatures features) {
        List<String> serverMechanisms = features.mechanisms();
        String preferred = config.getSaslMechanism();

        selectedMechanism = null;
        if (preferred != null) {
            for (SaslMechanism mech : mechanisms) {
                if (mech.getMechanismName().equals(preferred) && serverMechanisms.contains(preferred)) {
                    selectedMechanism = mech;
                    break;
                }
            }
        }

        if (selectedMechanism == null) {
            for (SaslMechanism mech : mechanisms) {
                if (serverMechanisms.contains(mech.getMechanismName())) {
                    selectedMechanism = mech;
                    break;
                }
            }
        }

        if (selectedMechanism == null) {
            log.error("No compatible SASL mechanism found for device {}", connection.getDeviceId());
            connection.setState(XmppConnection.ConnectionState.FAILED);
            return;
        }

        connection.setState(XmppConnection.ConnectionState.AUTHENTICATING);
        String username = connection.getXmppUsername();
        if (username == null || username.isBlank()) {
            username = connection.getDeviceId().toLowerCase(Locale.ROOT);
        }
        authUsername = username;
        String password = connection.getXmppPassword() != null ? connection.getXmppPassword() : config.getPassword();
        authPassword = password;
        String initialResponse = selectedMechanism.getInitialResponse(username, password);
        connection.sendStanza(new XmppStanza.AuthStanza(selectedMechanism.getMechanismName(), initialResponse));
        log.debug("SASL {} auth started for device {}", selectedMechanism.getMechanismName(), connection.getDeviceId());
    }

    public void handleAuthResult(XmppStanza stanza) {
        if (stanza instanceof XmppStanza.AuthSuccess) {
            XmppStanza.AuthSuccess success = (XmppStanza.AuthSuccess) stanza;
            if (selectedMechanism != null && !selectedMechanism.verifyServerFinal(success.payload())) {
                log.error("SASL server signature verification failed for device {}", connection.getDeviceId());
                connection.setState(XmppConnection.ConnectionState.FAILED);
                return;
            }
            log.debug("SASL auth succeeded for device {}", connection.getDeviceId());
            connection.setState(XmppConnection.ConnectionState.AUTHENTICATED);
            resetDecoder();
            connection.sendStanza(new XmppStanza.StreamOpen(null, config.getDomain(), null));
        } else if (stanza instanceof XmppStanza.AuthFailure failure) {
            log.error("SASL auth failed for device {}: {}", connection.getDeviceId(), failure.payload());
            connection.setState(XmppConnection.ConnectionState.FAILED);
        }
    }

    public void handleChallenge(XmppStanza.AuthChallenge challenge) {
        log.debug("Received SASL challenge for device {}", connection.getDeviceId());
        String response = selectedMechanism.getChallengeResponse(challenge.payload(), authUsername, authPassword);
        if (response == null) {
            log.error("SASL challenge response calculation failed for device {}", connection.getDeviceId());
            connection.setState(XmppConnection.ConnectionState.FAILED);
        } else {
            log.debug("Sending SASL challenge response for device {}", connection.getDeviceId());
            connection.sendStanza(new XmppStanza.AuthResponse(response));
        }
    }

    public SaslMechanism getSelectedMechanism() {
        return selectedMechanism;
    }

    private void resetDecoder() {
        Channel ch = connection.getChannel();
        if (ch != null) {
            XmppStreamDecoder decoder = ch.pipeline().get(XmppStreamDecoder.class);
            if (decoder != null) {
                decoder.reset();
                log.debug("XMPP stream decoder reset for device {}", connection.getDeviceId());
            } else {
                log.warn("XMPP stream decoder not found in pipeline for device {}", connection.getDeviceId());
            }
        }
    }
}
