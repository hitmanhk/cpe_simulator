package com.cpe.acsit.xmpp;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Record of one client session as seen by the test XMPP server.
 */
public class ClientSession {

    public enum State { CONNECTED, TLS_NEGOTIATING, AUTHENTICATING, AUTHENTICATED,
        BINDING, ONLINE, CLOSED }

    private final int id;
    private final long connectedAtMillis = System.currentTimeMillis();
    private final List<String> receivedStanzas = new CopyOnWriteArrayList<>();
    private final BlockingQueue<String> stanzaSink = new LinkedBlockingQueue<>();

    private volatile State state = State.CONNECTED;
    private volatile String username;
    private volatile String jid;
    private volatile String resource;
    private volatile String mechanism;
    private volatile int pingCount;
    private volatile long onlineAtMillis;

    ClientSession(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public State state() {
        return state;
    }

    void state(State newState) {
        this.state = newState;
        if (newState == State.ONLINE) {
            onlineAtMillis = System.currentTimeMillis();
        }
    }

    public String username() {
        return username;
    }

    void username(String username) {
        this.username = username;
    }

    public String jid() {
        return jid;
    }

    void jid(String jid) {
        this.jid = jid;
    }

    public String resource() {
        return resource;
    }

    void resource(String resource) {
        this.resource = resource;
    }

    public String mechanism() {
        return mechanism;
    }

    void mechanism(String mechanism) {
        this.mechanism = mechanism;
    }

    public int pingCount() {
        return pingCount;
    }

    void incrementPingCount() {
        pingCount++;
    }

    public long connectedAtMillis() {
        return connectedAtMillis;
    }

    public long onlineAtMillis() {
        return onlineAtMillis;
    }

    /** All stanzas received on this session, in order. */
    public List<String> receivedStanzas() {
        return receivedStanzas;
    }

    void record(String stanza) {
        receivedStanzas.add(stanza);
        stanzaSink.offer(stanza);
    }

    /** Waits for a stanza whose content contains the given fragment. */
    public String awaitStanza(String fragment, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return null;
            }
            String stanza = stanzaSink.poll(remaining, TimeUnit.MILLISECONDS);
            if (stanza != null && stanza.contains(fragment)) {
                return stanza;
            }
        }
    }
}
