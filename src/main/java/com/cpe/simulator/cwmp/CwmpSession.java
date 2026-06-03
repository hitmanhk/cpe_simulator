package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CwmpSession {

    public enum State { IDLE, INFORM_SENT, CPE_HOLD, ACS_HOLD }

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
        if (state != State.IDLE) {
            throw new IllegalStateException("Cannot start session from state: " + state);
        }
        this.sessionId = IdGenerator.uuid();
        this.startTime = Instant.now();
        this.lastActivity = Instant.now();
        this.pendingEvents = new ArrayList<>(events);
        this.pendingTransfers = new ArrayList<>();
        this.lastResponse = null;
        this.state = State.INFORM_SENT;
    }

    public void informSent() {
        if (state != State.INFORM_SENT) {
            throw new IllegalStateException("Cannot transition to CPE_HOLD from state: " + state);
        }
        this.lastActivity = Instant.now();
        this.state = State.CPE_HOLD;
    }

    public void informResponseReceived(boolean holdRequests) {
        if (state != State.CPE_HOLD) {
            throw new IllegalStateException("Cannot process InformResponse from state: " + state);
        }
        this.lastActivity = Instant.now();
        if (holdRequests) {
            this.state = State.ACS_HOLD;
        }
    }

    public void acsRequestProcessed() {
        if (state != State.ACS_HOLD) {
            throw new IllegalStateException("Cannot transition from state: " + state);
        }
        this.lastActivity = Instant.now();
        this.state = State.CPE_HOLD;
    }

    public void sendEmptyRequest() {
        if (state != State.CPE_HOLD) {
            throw new IllegalStateException("Cannot send empty request from state: " + state);
        }
        this.lastActivity = Instant.now();
        this.state = State.ACS_HOLD;
    }

    public void sessionComplete() {
        this.lastActivity = Instant.now();
        this.state = State.IDLE;
    }

    public boolean isTimedOut() {
        if (state == State.IDLE) {
            return false;
        }
        return Duration.between(lastActivity, Instant.now()).getSeconds() > timeoutSeconds;
    }

    public void reset() {
        this.state = State.IDLE;
        this.sessionId = null;
        this.startTime = null;
        this.lastActivity = null;
        this.pendingEvents = new ArrayList<>();
        this.pendingTransfers = new ArrayList<>();
        this.lastResponse = null;
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
