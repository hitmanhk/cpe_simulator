package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CwmpSession {

    public enum State { IDLE, IN_SESSION }

    public record PendingTransfer(String commandKey, String fileType, int faultCode, String startTime, String completeTime) {}

    private State state = State.IDLE;
    private String sessionId;
    private Instant startTime;
    private Instant lastActivity;
    private int timeoutSeconds;
    private List<SoapEnvelope.EventStruct> pendingEvents;
    private List<PendingTransfer> pendingTransfers;
    private SoapEnvelope lastResponse;

    public CwmpSession(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.pendingEvents = new ArrayList<>();
        this.pendingTransfers = new ArrayList<>();
    }

    public void startSession(List<SoapEnvelope.EventStruct> events) {
        if (state == State.IN_SESSION) {
            close();
        }
        this.sessionId = IdGenerator.uuid();
        this.startTime = Instant.now();
        this.lastActivity = Instant.now();
        this.pendingEvents = new ArrayList<>(events);
        this.pendingTransfers = new ArrayList<>();
        this.lastResponse = null;
        this.state = State.IN_SESSION;
    }

    public void close() {
        this.state = State.IDLE;
        this.sessionId = null;
        this.startTime = null;
        this.lastActivity = null;
        this.pendingEvents = new ArrayList<>();
        this.pendingTransfers = new ArrayList<>();
        this.lastResponse = null;
    }

    public boolean isTimedOut() {
        if (state == State.IDLE) {
            return false;
        }
        return Duration.between(lastActivity, Instant.now()).getSeconds() > timeoutSeconds;
    }

    public State getState() {
        return state;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public List<SoapEnvelope.EventStruct> getPendingEvents() {
        return pendingEvents;
    }

    public List<PendingTransfer> getPendingTransfers() {
        return pendingTransfers;
    }

    public void addPendingTransfer(PendingTransfer transfer) {
        this.pendingTransfers.add(transfer);
    }

    public void clearPendingTransfers() {
        this.pendingTransfers.clear();
    }

    public SoapEnvelope getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(SoapEnvelope lastResponse) {
        this.lastResponse = lastResponse;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }
}
