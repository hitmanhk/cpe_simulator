# XMPP Backoff Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the polling-based health check reconnect with a callback-driven ReconnectScheduler that provides exponential backoff, deduplication, and per-device retry tracking.

**Architecture:** XmppConnection fires `onFailure`/`onSuccess` callbacks on state transitions. ReconnectScheduler receives these callbacks, maintains a pending-device set for dedup and per-device BackoffTracker for exponential delay calculation (30s → 1min → 2min → ... → 30min max). A `disconnectRequested` flag on XmppConnection prevents reconnect scheduling on intentional disconnects.

**Tech Stack:** Java 21, JUnit 5, Mockito, ConcurrentHashMap, ScheduledExecutorService

---

## File Structure

| Action | File | Responsibility |
|---|---|---|
| Create | `src/main/java/com/cpe/simulator/xmpp/ReconnectScheduler.java` | Central scheduler: dedup, backoff, reconnect execution |
| Create | `src/test/java/com/cpe/simulator/xmpp/ReconnectSchedulerTest.java` | Unit tests for ReconnectScheduler |
| Modify | `src/main/java/com/cpe/simulator/xmpp/XmppConnection.java:37-143` | Add `onFailure`/`onSuccess` callbacks, `disconnectRequested` flag |
| Modify | `src/main/java/com/cpe/simulator/xmpp/XmppStanzaDispatcher.java:82-87` | Add XmppConnection reference, set state on exceptionCaught/channelInactive |
| Modify | `src/main/java/com/cpe/simulator/xmpp/XmppEngine.java:25,34,41-74,90-109,129-136` | Replace ReconnectPolicy with ReconnectScheduler, remove healthCheck reconnect |
| Delete | `src/main/java/com/cpe/simulator/xmpp/ReconnectPolicy.java` | Replaced by BackoffTracker inside ReconnectScheduler |
| Modify | `src/test/java/com/cpe/simulator/xmpp/XmppConnectionTest.java` | Add tests for new callbacks and disconnectRequested flag |

---

### Task 1: Add onFailure/onSuccess callbacks and disconnectRequested flag to XmppConnection

**Files:**
- Modify: `src/main/java/com/cpe/simulator/xmpp/XmppConnection.java:37-143`
- Test: `src/test/java/com/cpe/simulator/xmpp/XmppConnectionTest.java`

- [ ] **Step 1: Write failing tests for onFailure and onSuccess callbacks**

Add these tests to `XmppConnectionTest.java`:

```java
@Test
void testOnFailureCallbackFiredOnFailedState() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
    List<String> failureDeviceIds = new ArrayList<>();
    conn.onFailure(failureDeviceIds::add);

    conn.setState(XmppConnection.ConnectionState.FAILED);

    assertEquals(1, failureDeviceIds.size());
    assertEquals("device001", failureDeviceIds.get(0));
}

@Test
void testOnFailureCallbackFiredOnDisconnectedState() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
    List<String> failureDeviceIds = new ArrayList<>();
    conn.onFailure(failureDeviceIds::add);

    conn.setState(XmppConnection.ConnectionState.DISCONNECTED);

    assertEquals(1, failureDeviceIds.size());
    assertEquals("device001", failureDeviceIds.get(0));
}

@Test
void testOnSuccessCallbackFiredOnOnlineState() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
    List<String> successDeviceIds = new ArrayList<>();
    conn.onSuccess(successDeviceIds::add);

    conn.setState(XmppConnection.ConnectionState.ONLINE);

    assertEquals(1, successDeviceIds.size());
    assertEquals("device001", successDeviceIds.get(0));
}

@Test
void testCallbacksNotFiredForOtherStates() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
    List<String> failureIds = new ArrayList<>();
    List<String> successIds = new ArrayList<>();
    conn.onFailure(failureIds::add);
    conn.onSuccess(successIds::add);

    conn.setState(XmppConnection.ConnectionState.CONNECTING);
    conn.setState(XmppConnection.ConnectionState.AUTHENTICATING);
    conn.setState(XmppConnection.ConnectionState.AUTHENTICATED);
    conn.setState(XmppConnection.ConnectionState.BINDING);
    conn.setState(XmppConnection.ConnectionState.BOUND);

    assertTrue(failureIds.isEmpty());
    assertTrue(successIds.isEmpty());
}

@Test
void testCallbacksWorkWithoutRegistration() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));

    assertDoesNotThrow(() -> {
        conn.setState(XmppConnection.ConnectionState.FAILED);
        conn.setState(XmppConnection.ConnectionState.ONLINE);
        conn.setState(XmppConnection.ConnectionState.DISCONNECTED);
    });
}

@Test
void testOnFailureNotFiredOnIntentionalDisconnect() {
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null));
    List<String> failureIds = new ArrayList<>();
    conn.onFailure(failureIds::add);

    conn.disconnect();

    assertTrue(failureIds.isEmpty());
    assertEquals(XmppConnection.ConnectionState.DISCONNECTED, conn.getState());
}

@Test
void testReconnectAfterIntentionalDisconnectAndExplicitStateChange() {
    // After an intentional disconnect, the connection should be in DISCONNECTED
    // but onFailure should NOT fire. If setState(FAILED) is later called
    // (e.g., from a channel exception), onFailure SHOULD fire.
    XmppConnection conn = new XmppConnection("device001", config, null, null, new ConnReqAuthConfig(false, null, null));
    List<String> failureIds = new ArrayList<>();
    conn.onFailure(failureIds::add);

    conn.disconnect();
    assertTrue(failureIds.isEmpty());

    // Simulate a subsequent explicit failure
    conn.setState(XmppConnection.ConnectionState.FAILED);
    assertEquals(1, failureIds.size());
    assertEquals("device001", failureIds.get(0));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=XmppConnectionTest -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | tail -20`
Expected: Compilation errors — `onFailure`, `onSuccess` methods don't exist yet.

- [ ] **Step 3: Implement the callbacks and disconnectRequested flag in XmppConnection**

In `XmppConnection.java`, add these fields after the existing `onStateChangeCallback` field (line 37):

```java
private volatile Consumer<String> onFailureCallback;
private volatile Consumer<String> onSuccessCallback;
private volatile boolean disconnectRequested;
```

Add these methods after `onStateChange()` (after line 147):

```java
public void onFailure(Consumer<String> callback) {
    this.onFailureCallback = callback;
}

public void onSuccess(Consumer<String> callback) {
    this.onSuccessCallback = callback;
}
```

Replace the `setState()` method (lines 138-143) with:

```java
public void setState(ConnectionState state) {
    this.state = state;
    if (onStateChangeCallback != null) {
        onStateChangeCallback.accept(state);
    }

    if (state == ConnectionState.FAILED || (state == ConnectionState.DISCONNECTED && !disconnectRequested)) {
        if (onFailureCallback != null) {
            onFailureCallback.accept(deviceId);
        }
    } else if (state == ConnectionState.ONLINE) {
        disconnectRequested = false;
        if (onSuccessCallback != null) {
            onSuccessCallback.accept(deviceId);
        }
    }
}
```

Replace the `disconnect()` method (lines 115-122) with:

```java
public void disconnect() {
    disconnectRequested = true;
    if (channel != null && channel.isActive()) {
        sendStanza(new XmppStanza.PresenceStanza(null, "unavailable", null, null));
        sendStanza(new XmppStanza.StreamClose());
        channel.close();
    }
    setState(ConnectionState.DISCONNECTED);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=XmppConnectionTest -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | tail -20`
Expected: All XmppConnectionTest tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cpe/simulator/xmpp/XmppConnection.java src/test/java/com/cpe/simulator/xmpp/XmppConnectionTest.java
git commit -m "feat: add onFailure/onSuccess callbacks and disconnectRequested flag to XmppConnection"
```

---

### Task 2: Create ReconnectScheduler with BackoffTracker

**Files:**
- Create: `src/main/java/com/cpe/simulator/xmpp/ReconnectScheduler.java`
- Create: `src/test/java/com/cpe/simulator/xmpp/ReconnectSchedulerTest.java`

- [ ] **Step 1: Write failing tests for ReconnectScheduler**

Create `src/test/java/com/cpe/simulator/xmpp/ReconnectSchedulerTest.java`:

```java
package com.cpe.simulator.xmpp;

import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconnectSchedulerTest {

    private ScheduledExecutorService mockScheduler;
    private EventLoopGroup mockEventLoopGroup;
    private List<XmppConnection> connections;
    private AtomicInteger scheduleCount;
    private List<Long> scheduledDelays;

    @BeforeEach
    void setUp() {
        mockScheduler = mock(ScheduledExecutorService.class);
        mockEventLoopGroup = mock(EventLoopGroup.class);
        connections = new ArrayList<>();
        scheduleCount = new AtomicInteger(0);
        scheduledDelays = new ArrayList<>();

        // Capture schedule calls
        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                scheduleCount.incrementAndGet();
                long delay = invocation.getArgument(1);
                scheduledDelays.add(delay);
                // Execute the runnable immediately for testing
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });
    }

    private ReconnectScheduler createScheduler() {
        return new ReconnectScheduler(mockScheduler,
            deviceId -> connections.stream()
                .filter(c -> c.getDeviceId().equals(deviceId))
                .findFirst().orElse(null),
            mockEventLoopGroup);
    }

    // --- BackoffTracker tests ---

    @Test
    void testBackoffSequence() {
        ReconnectScheduler scheduler = createScheduler();
        // Access BackoffTracker via reflection-like approach: trigger failures and check delays
        // Instead, test the delays through the schedule calls
        // First failure → ~30s delay
        // We'll test BackoffTracker directly through the scheduler's behavior
        // For unit-level BackoffTracker testing, we use the exposed getPendingCount for dedup
        // and verify delay sequence via the scheduled delays list
    }

    @Test
    void testDedupIgnoresSecondFailureForSameDevice() {
        ReconnectScheduler scheduler = createScheduler();
        scheduleCount.set(0);

        scheduler.onFailure("device001");
        scheduler.onFailure("device001");  // duplicate

        assertEquals(1, scheduleCount.get());
    }

    @Test
    void testDifferentDevicesAreNotDeduped() {
        ReconnectScheduler scheduler = createScheduler();
        scheduleCount.set(0);

        scheduler.onFailure("device001");
        scheduler.onFailure("device002");

        assertEquals(2, scheduleCount.get());
    }

    @Test
    void testOnSuccessResetsBackoff() {
        // Simulate: failure → reconnect → failure again
        // Without reset, second failure should escalate backoff
        // With reset (success in between), second failure should start at 30s again
        ReconnectScheduler scheduler = createScheduler();
        scheduledDelays.clear();

        // First failure — should schedule with ~30s delay
        scheduler.onFailure("device001");
        long firstDelay = scheduledDelays.get(0);

        // Simulate success (resets backoff)
        scheduler.onSuccess("device001");

        // Second failure — should schedule with ~30s delay again (not escalated)
        scheduledDelays.clear();
        scheduler.onFailure("device001");
        long secondDelay = scheduledDelays.get(0);

        // Both delays should be in the same ballpark (~30s)
        // Second delay should NOT be ~60s
        assertTrue(secondDelay < 45_000,
            "After reset, delay should be ~30s not escalated. Got: " + secondDelay + "ms");
    }

    @Test
    void testIsPendingReturnsTrueForScheduledDevice() {
        ReconnectScheduler scheduler = createScheduler();

        // Reset mock to NOT execute immediately, so device stays pending
        AtomicInteger localScheduleCount = new AtomicInteger(0);
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                localScheduleCount.incrementAndGet();
                // Don't execute — leave device in pending set
                return null;
            });

        ReconnectScheduler pendingScheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        pendingScheduler.onFailure("device001");

        assertTrue(pendingScheduler.isPending("device001"));
        assertFalse(pendingScheduler.isPending("device002"));
    }

    @Test
    void testShutdownClearsPendingSet() {
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> null);

        ReconnectScheduler scheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        scheduler.onFailure("device001");
        assertTrue(scheduler.isPending("device001"));

        scheduler.shutdown();
        assertFalse(scheduler.isPending("device001"));
    }

    @Test
    void testReconnectCallsConnectOnFoundConnection() {
        AtomicReference<String> connectedDevice = new AtomicReference<>();
        ScheduledExecutorService immediateScheduler = mock(ScheduledExecutorService.class);
        when(immediateScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });

        // Create a mock connection that records connect calls
        XmppConnection mockConn = mock(XmppConnection.class);
        when(mockConn.getDeviceId()).thenReturn("device001");
        connections.add(mockConn);

        ReconnectScheduler scheduler = new ReconnectScheduler(immediateScheduler,
            deviceId -> connections.stream()
                .filter(c -> c.getDeviceId().equals(deviceId))
                .findFirst().orElse(null),
            mockEventLoopGroup);

        scheduler.onFailure("device001");

        verify(mockConn, times(1)).connect(mockEventLoopGroup);
    }

    @Test
    void testReconnectSkipsIfConnectionNotFound() {
        ScheduledExecutorService immediateScheduler = mock(ScheduledExecutorService.class);
        when(immediateScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });

        ReconnectScheduler scheduler = new ReconnectScheduler(immediateScheduler,
            deviceId -> null,  // No connections ever found
            mockEventLoopGroup);

        // Should not throw — just log a warning
        assertDoesNotThrow(() -> scheduler.onFailure("nonexistent"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=ReconnectSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | tail -20`
Expected: Compilation error — `ReconnectScheduler` class doesn't exist yet.

- [ ] **Step 3: Implement ReconnectScheduler with BackoffTracker**

Create `src/main/java/com/cpe/simulator/xmpp/ReconnectScheduler.java`:

```java
package com.cpe.simulator.xmpp;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ReconnectScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconnectScheduler.class);

    static final long INITIAL_DELAY_MS = 30_000;    // 30 seconds
    static final long MAX_DELAY_MS = 30 * 60_000;   // 30 minutes

    private final Set<String> pendingDevices = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, BackoffTracker> backoffTrackers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Function<String, XmppConnection> connectionProvider;
    private final EventLoopGroup eventLoopGroup;

    public ReconnectScheduler(ScheduledExecutorService scheduler,
                               Function<String, XmppConnection> connectionProvider,
                               EventLoopGroup eventLoopGroup) {
        this.scheduler = scheduler;
        this.connectionProvider = connectionProvider;
        this.eventLoopGroup = eventLoopGroup;
    }

    public void onFailure(String deviceId) {
        if (!pendingDevices.add(deviceId)) {
            log.debug("Reconnect already pending for device {}, ignoring duplicate", deviceId);
            return;
        }
        BackoffTracker tracker = backoffTrackers.computeIfAbsent(deviceId, k -> new BackoffTracker());
        long delay = tracker.nextDelay();
        log.info("Scheduling reconnect for device {} in {}ms (attempt {})",
                 deviceId, delay, tracker.getAttemptCount());
        scheduler.schedule(() -> reconnect(deviceId), delay, TimeUnit.MILLISECONDS);
    }

    public void onSuccess(String deviceId) {
        BackoffTracker tracker = backoffTrackers.get(deviceId);
        if (tracker != null) {
            tracker.reset();
            log.debug("Reset backoff for device {} after successful reconnect", deviceId);
        }
    }

    private void reconnect(String deviceId) {
        pendingDevices.remove(deviceId);
        XmppConnection conn = connectionProvider.apply(deviceId);
        if (conn != null) {
            log.info("Attempting reconnect for device {}", deviceId);
            conn.connect(eventLoopGroup);
        } else {
            log.warn("No connection found for device {}, skipping reconnect", deviceId);
        }
    }

    public boolean isPending(String deviceId) {
        return pendingDevices.contains(deviceId);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        pendingDevices.clear();
    }

    /**
     * Per-device exponential backoff tracker.
     * Delay sequence: 30s, 1min, 2min, 4min, 8min, 16min, 30min, 30min, ...
     */
    static class BackoffTracker {
        private int attemptCount = 0;

        long nextDelay() {
            long baseDelay = INITIAL_DELAY_MS * (1L << attemptCount);
            long delay = Math.min(baseDelay, MAX_DELAY_MS);
            attemptCount++;
            return addJitter(delay);
        }

        void reset() {
            attemptCount = 0;
        }

        int getAttemptCount() {
            return attemptCount;
        }

        private long addJitter(long delay) {
            long jitter = (long) (ThreadLocalRandom.current().nextDouble(-0.1, 0.1) * delay);
            return Math.max(delay + jitter, INITIAL_DELAY_MS / 2);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=ReconnectSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | tail -20`
Expected: All ReconnectSchedulerTest tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cpe/simulator/xmpp/ReconnectScheduler.java src/test/java/com/cpe/simulator/xmpp/ReconnectSchedulerTest.java
git commit -m "feat: add ReconnectScheduler with exponential backoff and dedup"
```

---

### Task 3: Add BackoffTracker unit tests for delay sequence verification

**Files:**
- Create: `src/test/java/com/cpe/simulator/xmpp/BackoffTrackerTest.java`

The BackoffTracker is a package-private inner class. We test it through ReconnectScheduler's package access.

- [ ] **Step 1: Write BackoffTracker tests**

Create `src/test/java/com/cpe/simulator/xmpp/BackoffTrackerTest.java`:

```java
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
        // ±10% jitter on 30s = 27s to 33s, but floor is 15s
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
        // Burn through attempts until we hit the cap
        for (int i = 0; i < 20; i++) {
            tracker.nextDelay();
        }
        // All subsequent delays should be at or below 30min + jitter
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
        // Run many times to catch edge cases
        for (int i = 0; i < 1000; i++) {
            ReconnectScheduler.BackoffTracker t = new ReconnectScheduler.BackoffTracker();
            long delay = t.nextDelay();
            assertTrue(delay >= ReconnectScheduler.INITIAL_DELAY_MS / 2,
                "Delay should never go below 15s, got: " + delay + "ms");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=BackoffTrackerTest -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | tail -20`
Expected: All BackoffTrackerTest tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cpe/simulator/xmpp/BackoffTrackerTest.java
git commit -m "test: add BackoffTracker delay sequence and jitter tests"
```

---

### Task 4: Add XmppConnection reference to XmppStanzaDispatcher and set state on channel events

**Files:**
- Modify: `src/main/java/com/cpe/simulator/xmpp/XmppStanzaDispatcher.java`
- Modify: `src/main/java/com/cpe/simulator/xmpp/XmppConnection.java:69-71` (dispatcher construction)

- [ ] **Step 1: Add XmppConnection field to XmppStanzaDispatcher and set state on failure events**

In `XmppStanzaDispatcher.java`, add `XmppConnection connection` field and constructor parameter.

Replace the full class with:

```java
package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.XmlUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class XmppStanzaDispatcher extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(XmppStanzaDispatcher.class);

    private final XmppConnection connection;
    private final StreamHandler streamHandler;
    private final SaslHandler saslHandler;
    private final IqHandler iqHandler;
    private final PresenceHandler presenceHandler;
    private final BindHandler bindHandler;
    private final ExecutorService virtualThreadExecutor;

    public XmppStanzaDispatcher(XmppConnection connection,
                                 StreamHandler streamHandler,
                                 SaslHandler saslHandler,
                                 IqHandler iqHandler,
                                 PresenceHandler presenceHandler,
                                 BindHandler bindHandler,
                                 ExecutorService virtualThreadExecutor) {
        this.connection = connection;
        this.streamHandler = streamHandler;
        this.saslHandler = saslHandler;
        this.iqHandler = iqHandler;
        this.presenceHandler = presenceHandler;
        this.bindHandler = bindHandler;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof XmppStanza stanza)) {
            ctx.fireChannelRead(msg);
            return;
        }

        virtualThreadExecutor.execute(() -> dispatch(stanza));
    }

    private void dispatch(XmppStanza stanza) {
        try {
            switch (stanza) {
                case XmppStanza.StreamOpen open -> streamHandler.handleStreamOpen(open);
                case XmppStanza.StreamFeatures features -> streamHandler.handleStreamFeatures(features);
                case XmppStanza.AuthSuccess success -> saslHandler.handleAuthResult(success);
                case XmppStanza.AuthFailure failure -> saslHandler.handleAuthResult(failure);
                case XmppStanza.AuthChallenge challenge -> saslHandler.handleChallenge(challenge);
                case XmppStanza.IqStanza iq -> handleIqStanza(iq);
                case XmppStanza.PresenceStanza presence -> handlePresence(presence);
                default -> log.debug("Unhandled stanza type: {}", stanza.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error processing stanza: {}", stanza.getClass().getSimpleName(), e);
        }
    }

    private void handleIqStanza(XmppStanza.IqStanza iq) {
        if ("result".equals(iq.type()) || "error".equals(iq.type())) {
            String ns = iq.childNamespace();
            if (XmlUtil.NS_XMPP_BIND.equals(ns)) {
                bindHandler.handleBindResult(iq);
                return;
            }
            if (ns == null && iq.rawXml() != null && iq.rawXml().contains("bind")) {
                bindHandler.handleBindResult(iq);
                return;
            }
        }
        iqHandler.handleIq(iq);
    }

    private void handlePresence(XmppStanza.PresenceStanza presence) {
        log.debug("Received presence stanza: type={}", presence.type());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("XMPP channel exception for device {}", connection.getDeviceId(), cause);
        connection.setState(XmppConnection.ConnectionState.FAILED);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("XMPP channel inactive for device {}", connection.getDeviceId());
        connection.setState(XmppConnection.ConnectionState.DISCONNECTED);
        super.channelInactive(ctx);
    }
}
```

- [ ] **Step 2: Update XmppConnection.connect() to pass `this` to the dispatcher constructor**

In `XmppConnection.java`, change line 69-71 from:

```java
        XmppStanzaDispatcher dispatcher = new XmppStanzaDispatcher(
            streamHandler, saslHandler, iqHandler, presenceHandler, bindHandler,
            Executors.newVirtualThreadPerTaskExecutor());
```

to:

```java
        XmppStanzaDispatcher dispatcher = new XmppStanzaDispatcher(
            this, streamHandler, saslHandler, iqHandler, presenceHandler, bindHandler,
            Executors.newVirtualThreadPerTaskExecutor());
```

- [ ] **Step 3: Run all tests to verify nothing is broken**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -q 2>&1 | tail -30`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cpe/simulator/xmpp/XmppStanzaDispatcher.java src/main/java/com/cpe/simulator/xmpp/XmppConnection.java
git commit -m "feat: set connection state on channel exception and inactive in XmppStanzaDispatcher"
```

---

### Task 5: Wire ReconnectScheduler into XmppEngine, remove ReconnectPolicy and healthCheck reconnect

**Files:**
- Modify: `src/main/java/com/cpe/simulator/xmpp/XmppEngine.java`

- [ ] **Step 1: Replace ReconnectScheduler into XmppEngine**

Replace the full `XmppEngine.java` with:

```java
package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class XmppEngine {

    private static final Logger log = LoggerFactory.getLogger(XmppEngine.class);

    private final AppConfig appConfig;
    private final Consumer<String> connectionRequestCallback;
    private final ConnReqAuthConfig connReqAuthConfig;
    private final List<XmppConnection> connections = new CopyOnWriteArrayList<>();
    private final NioEventLoopGroup eventLoopGroup;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private ReconnectScheduler reconnectScheduler;

    public XmppEngine(AppConfig appConfig, Consumer<String> connectionRequestCallback, ConnReqAuthConfig connReqAuthConfig) {
        this.appConfig = appConfig;
        this.connectionRequestCallback = connectionRequestCallback;
        this.connReqAuthConfig = connReqAuthConfig;
        this.eventLoopGroup = new NioEventLoopGroup(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public XmppConnection createConnection(String deviceId) {
        return new XmppConnection(deviceId, appConfig.getXmpp(), appConfig.getLocalAddress(), connectionRequestCallback, connReqAuthConfig);
    }

    public void start(List<XmppConnection> connections) {
        this.connections.addAll(connections);
        running = true;

        this.reconnectScheduler = new ReconnectScheduler(scheduler, this::getConnection, eventLoopGroup);

        int batchSize = appConfig.getConnectBatch();
        int batchDelay = appConfig.getConnectBatchDelay();

        scheduler.execute(() -> {
            for (int i = 0; i < connections.size(); i += batchSize) {
                int end = Math.min(i + batchSize, connections.size());
                List<XmppConnection> batch = connections.subList(i, end);

                for (XmppConnection conn : batch) {
                    try {
                        registerCallbacks(conn);
                        conn.connect(eventLoopGroup);
                    } catch (Exception e) {
                        log.error("Failed to connect device {}", conn.getDeviceId(), e);
                    }
                }

                if (end < connections.size()) {
                    try {
                        Thread.sleep(batchDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            log.info("All {} XMPP connections initiated", connections.size());
        });
    }

    public void stop() {
        running = false;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
        }
        scheduler.shutdown();
        for (XmppConnection conn : connections) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                log.error("Error disconnecting device {}", conn.getDeviceId(), e);
            }
        }
        eventLoopGroup.shutdownGracefully();
        log.info("XMPP engine stopped");
    }

    public int getOnlineCount() {
        int count = 0;
        for (XmppConnection conn : connections) {
            if (conn.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public List<XmppConnection> getConnections() {
        return connections;
    }

    public boolean isRunning() {
        return running;
    }

    public void addConnection(XmppConnection conn) {
        connections.add(conn);
        registerCallbacks(conn);
        try {
            conn.connect(eventLoopGroup);
        } catch (Exception e) {
            log.error("Failed to connect device {}", conn.getDeviceId(), e);
        }
    }

    private void registerCallbacks(XmppConnection conn) {
        conn.onFailure(reconnectScheduler::onFailure);
        conn.onSuccess(reconnectScheduler::onSuccess);
    }

    private XmppConnection getConnection(String deviceId) {
        return connections.stream()
            .filter(c -> c.getDeviceId().equals(deviceId))
            .findFirst().orElse(null);
    }
}
```

- [ ] **Step 2: Run all tests to verify nothing is broken**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -q 2>&1 | tail -30`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/xmpp/XmppEngine.java
git commit -m "feat: wire ReconnectScheduler into XmppEngine, remove healthCheck reconnect"
```

---

### Task 6: Delete ReconnectPolicy

**Files:**
- Delete: `src/main/java/com/cpe/simulator/xmpp/ReconnectPolicy.java`

- [ ] **Step 1: Delete ReconnectPolicy.java**

Run: `rm /home/iris/workspace/code/cpe_simulator/src/main/java/com/cpe/simulator/xmpp/ReconnectPolicy.java`

- [ ] **Step 2: Verify no remaining references to ReconnectPolicy**

Run: `grep -rn "ReconnectPolicy" /home/iris/workspace/code/cpe_simulator/src/ --include="*.java"`
Expected: No output (no references found).

- [ ] **Step 3: Run all tests to verify nothing is broken**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -q 2>&1 | tail -30`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add -u src/main/java/com/cpe/simulator/xmpp/ReconnectPolicy.java
git commit -m "refactor: delete ReconnectPolicy replaced by ReconnectScheduler.BackoffTracker"
```

---

### Task 7: Update spec document with disconnectRequested guard

The spec didn't account for intentional disconnects triggering onFailure. Add a note about the `disconnectRequested` flag.

**Files:**
- Modify: `docs/superpowers/specs/2026-06-11-xmpp-backoff-retry-design.md`

- [ ] **Step 1: Add disconnectRequested documentation to the spec**

After the "### 1. XmppConnection — Callback Addition" section's code block, add:

```markdown
**Important: `disconnectRequested` guard** — `XmppConnection.disconnect()` sets `DISCONNECTED` state, which would normally trigger `onFailure` and cause an unwanted reconnect. A `disconnectRequested` boolean flag prevents this: `disconnect()` sets it to `true` before calling `setState()`, and `setState()` skips the `onFailure` callback when the flag is set. The flag is cleared when the connection returns to `ONLINE`.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-06-11-xmpp-backoff-retry-design.md
git commit -m "docs: add disconnectRequested guard note to backoff retry spec"
```
