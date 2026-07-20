package com.cpe.simulator.xmpp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackoffTrackerTest {

    private static final long INITIAL_DELAY_MS = 60_000;
    private static final long MAX_DELAY_MS = 1_200_000;
    private ReconnectScheduler.BackoffTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ReconnectScheduler.BackoffTracker(
            INITIAL_DELAY_MS, MAX_DELAY_MS, upperBound -> upperBound);
    }

    @Test
    void testUpperBoundDoublesAndCapsAtConfiguredMaximum() {
        assertEquals(60_000, tracker.nextDelay());
        assertEquals(120_000, tracker.nextDelay());
        assertEquals(240_000, tracker.nextDelay());
        assertEquals(480_000, tracker.nextDelay());
        assertEquals(960_000, tracker.nextDelay());
        assertEquals(1_200_000, tracker.nextDelay());
        assertEquals(1_200_000, tracker.nextDelay());
    }

    @Test
    void testFullJitterAllowsZeroDelay() {
        ReconnectScheduler.BackoffTracker zeroTracker =
            new ReconnectScheduler.BackoffTracker(
                INITIAL_DELAY_MS, MAX_DELAY_MS, upperBound -> 0);

        assertEquals(0, zeroTracker.nextDelay());
        assertEquals(0, zeroTracker.nextDelay());
    }

    @Test
    void testResetRestoresInitialUpperBound() {
        tracker.nextDelay();
        tracker.nextDelay();
        tracker.nextDelay();

        tracker.reset();

        assertEquals(INITIAL_DELAY_MS, tracker.nextDelay());
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
    void testUsesConfiguredInitialAndMaximumUpperBounds() {
        ReconnectScheduler.BackoffTracker configuredTracker =
            new ReconnectScheduler.BackoffTracker(
                10_000, 100_000, upperBound -> upperBound);

        assertEquals(10_000, configuredTracker.nextDelay());
        assertEquals(20_000, configuredTracker.nextDelay());
        assertEquals(40_000, configuredTracker.nextDelay());
        assertEquals(80_000, configuredTracker.nextDelay());
        assertEquals(100_000, configuredTracker.nextDelay());
        assertEquals(100_000, configuredTracker.nextDelay());
    }
}
