package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SaslHandler {

    private static final Logger log = LoggerFactory.getLogger(SaslHandler.class);

    private final XmppConnection connection;
    private final AppConfig.XmppConfig config;
    private final List<SaslMechanism> mechanisms;
    private SaslMechanism selectedMechanism;

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
        String username = config.getUserPrefix() + connection.getDeviceId();
        String initialResponse = selectedMechanism.getInitialResponse(username, config.getPassword());
        connection.sendStanza(new XmppStanza.AuthStanza(selectedMechanism.getMechanismName(), initialResponse));
        log.debug("SASL {} auth started for device {}", selectedMechanism.getMechanismName(), connection.getDeviceId());
    }

    public void handleAuthResult(XmppStanza stanza) {
        if (stanza instanceof XmppStanza.AuthSuccess) {
            log.debug("SASL auth succeeded for device {}", connection.getDeviceId());
            connection.setState(XmppConnection.ConnectionState.AUTHENTICATED);
            connection.sendStanza(new XmppStanza.StreamOpen(null, config.getDomain(), null));
        } else if (stanza instanceof XmppStanza.AuthFailure failure) {
            log.error("SASL auth failed for device {}: {}", connection.getDeviceId(), failure.payload());
            connection.setState(XmppConnection.ConnectionState.FAILED);
        }
    }

    public SaslMechanism getSelectedMechanism() {
        return selectedMechanism;
    }
}
