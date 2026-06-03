package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CwmpSessionTest {

    private CwmpSession session;

    @BeforeEach
    void setUp() {
        session = new CwmpSession(30);
    }

    @Test
    void sessionStartsIdle() {
        assertEquals(CwmpSession.State.IDLE, session.getState());
    }

    @Test
    void startSessionTransitionsToInSession() {
        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct("2 PERIODIC", ""));

        session.startSession(events);

        assertEquals(CwmpSession.State.IN_SESSION, session.getState());
        assertNotNull(session.getSessionId());
        assertNotNull(session.getStartTime());
        assertNotNull(session.getLastActivity());
        assertEquals(events, session.getPendingEvents());
    }

    @Test
    void closeTransitionsBackToIdle() {
        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct("2 PERIODIC", ""));

        session.startSession(events);
        assertEquals(CwmpSession.State.IN_SESSION, session.getState());

        session.close();
        assertEquals(CwmpSession.State.IDLE, session.getState());
        assertNull(session.getSessionId());
        assertNull(session.getStartTime());
        assertNull(session.getLastActivity());
    }

    @Test
    void idleToInSessionToIdleNoException() {
        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct("2 PERIODIC", ""));

        session.startSession(events);
        assertEquals(CwmpSession.State.IN_SESSION, session.getState());

        session.close();
        assertEquals(CwmpSession.State.IDLE, session.getState());

        session.startSession(events);
        assertEquals(CwmpSession.State.IN_SESSION, session.getState());

        session.close();
        assertEquals(CwmpSession.State.IDLE, session.getState());
    }

    @Test
    void closeOnAlreadyIdleIsNoOp() {
        assertEquals(CwmpSession.State.IDLE, session.getState());

        session.close();

        assertEquals(CwmpSession.State.IDLE, session.getState());
    }

    @Test
    void startSessionOnAlreadyInSessionClosesAndRestarts() {
        List<SoapEnvelope.EventStruct> events1 = new ArrayList<>();
        events1.add(new SoapEnvelope.EventStruct("2 PERIODIC", ""));

        session.startSession(events1);
        String firstSessionId = session.getSessionId();
        assertEquals(CwmpSession.State.IN_SESSION, session.getState());

        List<SoapEnvelope.EventStruct> events2 = new ArrayList<>();
        events2.add(new SoapEnvelope.EventStruct("4 VALUE CHANGE", "key1"));

        session.startSession(events2);

        assertEquals(CwmpSession.State.IN_SESSION, session.getState());
        assertNotNull(session.getSessionId());
        assertNotEquals(firstSessionId, session.getSessionId());
        assertEquals(events2, session.getPendingEvents());
    }

    @Test
    void isTimedOutReturnsFalseWhenIdle() {
        assertFalse(session.isTimedOut());
    }

    @Test
    void touchUpdatesLastActivity() {
        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct("2 PERIODIC", ""));
        session.startSession(events);

        var before = session.getLastActivity();
        session.touch();
        var after = session.getLastActivity();

        assertTrue(after.compareTo(before) >= 0);
    }
}