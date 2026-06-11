package com.cpe.simulator.xmpp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackoffTrackerTest {

    private ReconnectScheduler.BackoffTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ReconnectScheduler.BackoffTracker();
    }

    @Test
    void testFirstDelayAround30s() {
        long delay = tracker.nextDelay();
        assertTrue(delay >= 27_000 && delay <= 33_000,
            "First delay should be ~30s, got: " + delay + "ms");
    }

    @Test
    void testSecondDelayAround60s() {
        tracker.nextDelay(); // 30s
        long delay = tracker.nextDelay();
        assertTrue(delay >= 54_000 && delay <= 66_000,
            "Second delay should be ~60s, got: " + delay + "ms");
    }

    @Test
    void testThirdDelayAround120s() {
        tracker.nextDelay(); // 30s
        tracker.nextDelay(); // 60s
        long delay = tracker.nextDelay();
        assertTrue(delay >= 108_000 && delay <= 132_000,
            "Third delay should be ~2min, got: " + delay + "ms");
    }

    @Test
    void testDelayCapsAt30Minutes() {
        for (int i = 0; i < 20; i++) {
            tracker.nextDelay();
        }
        long delay = tracker.nextDelay();
        assertTrue(delay <= ReconnectScheduler.MAX_DELAY_MS * 1.1,
            "Delay should cap at ~30min, got: " + delay + "ms");
        assertTrue(delay >= ReconnectScheduler.MAX_DELAY_MS * 0.9,
            "Delay should be near 30min cap, got: " + delay + "ms");
    }

    @Test
    void testResetRestoresInitialDelay() {
        tracker.nextDelay(); // 30s
        tracker.nextDelay(); // 60s
        tracker.nextDelay(); // 120s

        tracker.reset();

        long delay = tracker.nextDelay();
        assertTrue(delay >= 27_000 && delay <= 33_000,
            "After reset, delay should be ~30s again, got: " + delay + "ms");
    }

    @Test
    void testAttemptCountIncrementsAfterNextDelay() {
        assertEquals(0, tracker.getAttemptCount());
        tracker.nextDelay();
        assertEquals(1, tracker.getAttemptCount());
        tracker.nextDelay();
        assertEquals(2, tracker.getAttemptCount());
    }

    @Test
    void testAttemptCountAfterReset() {
        tracker.nextDelay();
        tracker.nextDelay();
        assertEquals(2, tracker.getAttemptCount());
        tracker.reset();
        assertEquals(0, tracker.getAttemptCount());
    }

    @Test
    void testJitterNeverGoesBelow15s() {
        for (int i = 0; i < 1000; i++) {
            ReconnectScheduler.BackoffTracker t = new ReconnectScheduler.BackoffTracker();
            long delay = t.nextDelay();
            assertTrue(delay >= ReconnectScheduler.INITIAL_DELAY_MS / 2,
                "Delay should never go below 15s, got: " + delay + "ms");
        }
    }
}
