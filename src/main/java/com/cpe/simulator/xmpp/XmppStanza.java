package com.cpe.simulator.xmpp;

import java.util.List;

public sealed interface XmppStanza permits
    XmppStanza.IqStanza, XmppStanza.PresenceStanza, XmppStanza.MessageStanza,
    XmppStanza.StreamOpen, XmppStanza.StreamClose, XmppStanza.StreamFeatures,
    XmppStanza.AuthStanza, XmppStanza.AuthSuccess, XmppStanza.AuthFailure,
    XmppStanza.AuthChallenge, XmppStanza.AuthResponse,
    XmppStanza.BindStanza, XmppStanza.StartTls, XmppStanza.StartTlsProceed,
    XmppStanza.RawXml {
    String getId();

    record IqStanza(String id, String type, String from, String to,
                    String childElement, String childNamespace, String rawXml) implements XmppStanza {
        @Override
        public String getId() { return id; }
    }

    record PresenceStanza(String id, String type, String from, String to) implements XmppStanza {
        @Override
        public String getId() { return id; }
    }

    record MessageStanza(String id, String type, String from, String to, String body) implements XmppStanza {
        @Override
        public String getId() { return id; }
    }

    record StreamOpen(String from, String to, String id) implements XmppStanza {
        @Override
        public String getId() { return id; }
    }

    record StreamClose() implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record StreamFeatures(String rawXml, List<String> mechanisms, boolean hasBind,
                          boolean hasSession, boolean hasStartTls) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record AuthStanza(String mechanism, String payload) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record AuthSuccess(String payload) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record AuthFailure(String payload) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record AuthChallenge(String payload) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record AuthResponse(String payload) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record BindStanza(String id, String resource, String jid) implements XmppStanza {
        @Override
        public String getId() { return id; }
    }

    record StartTls() implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record StartTlsProceed() implements XmppStanza {
        @Override
        public String getId() { return null; }
    }

    record RawXml(String xml) implements XmppStanza {
        @Override
        public String getId() { return null; }
    }
}
