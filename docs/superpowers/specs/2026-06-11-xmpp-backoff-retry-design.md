# XMPP Connection Backoff Retry Design

## Problem

The current health check architecture polls all connections every 30 seconds via `XmppEngine.healthCheck()`, and reconnects any FAILED/DISCONNECTED connection immediately through `ReconnectPolicy`. This approach has several issues:

1. **No backoff penalty** — failed connections are retried with minimal delay (1s initial), hammering the server
2. **No failure-type differentiation** — authentication failures and network failures use the same aggressive retry
3. **No deduplication** — the same device can be scheduled for reconnect multiple times across health check cycles
4. **No callback mechanism** — the engine polls state rather than being notified of failures
5. **Missing failure triggers** — `XmppStanzaDispatcher.exceptionCaught()` and `channelInactive()` don't set connection state, so channel-level failures are invisible to the health check

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Retry strategy | Uniform exponential backoff for all failure types | Simple, no need to classify failure reasons |
| Backoff curve | 30s → 1min → 2min → 4min → 8min → 16min → 30min (max) | Exponential with 30s base, 30min ceiling |
| Retry limit | Infinite | Never give up on a device |
| Dedup strategy | Ignore duplicate requests for same device | First request wins, no timer reset |
| Backoff reset | Reset on successful reconnect | Each new failure chain starts fresh |
| Health check fallback | Removed | Pure callback-driven, no polling fallback |

## Architecture

### Component Overview

```
XmppConnection ──[onFailure callback]──→ ReconnectScheduler ──[delayed schedule]──→ XmppConnection.connect()
         ↑                                  │                                        │
         └────[onSuccess callback]──────────┘  (reset backoff tracker)              │
         └─────────────────────────────────────────────────────────────────────────┘
```

### Class Changes

| Class | Change | Description |
|---|---|---|
| `ReconnectScheduler` | **New** | Central scheduler managing backoff, dedup, and reconnect execution |
| `BackoffTracker` | **New (inner class)** | Per-device exponential backoff state, replaces `ReconnectPolicy` |
| `XmppConnection` | **Modified** | Add `onFailure`/`onSuccess` callbacks; fire them on state transitions |
| `XmppStanzaDispatcher` | **Modified** | Set FAILED/DISCONNECTED state on `exceptionCaught()` and `channelInactive()` |
| `XmppEngine` | **Modified** | Register scheduler callbacks instead of polling; remove healthCheck reconnect logic |
| `ReconnectPolicy` | **Deleted** | Replaced by `BackoffTracker` inside `ReconnectScheduler` |

## Detailed Design

### 1. XmppConnection — Callback Addition

Add two callback fields alongside the existing `onStateChangeCallback`:

```java
public class XmppConnection {
    // Existing
    private volatile Consumer<ConnectionState> onStateChangeCallback;

    // New: scheduler-specific callbacks
    private volatile Consumer<String> onFailureCallback;
    private volatile Consumer<String> onSuccessCallback;

    public void onFailure(Consumer<String> callback) { this.onFailureCallback = callback; }
    public void onSuccess(Consumer<String> callback) { this.onSuccessCallback = callback; }

    public void setState(ConnectionState state) {
        this.state = state;
        if (onStateChangeCallback != null) onStateChangeCallback.accept(state);

        // Trigger scheduler callbacks
        if (state == ConnectionState.FAILED || state == ConnectionState.DISCONNECTED) {
            if (onFailureCallback != null) onFailureCallback.accept(deviceId);
        } else if (state == ConnectionState.ONLINE) {
            if (onSuccessCallback != null) onSuccessCallback.accept(deviceId);
        }
    }
}
```

**Important: `disconnectRequested` guard** — `XmppConnection.disconnect()` sets `DISCONNECTED` state, which would normally trigger `onFailure` and cause an unwanted reconnect. A `disconnectRequested` boolean flag prevents this: `disconnect()` sets it to `true` before calling `setState()`, and `setState()` skips the `onFailure` callback when the flag is set. The flag is cleared when `connect()` is called (start of a reconnect cycle) and when the connection returns to `ONLINE` state.

**No change to `CpeDevice`** — it continues using `onStateChange()` for business-level state tracking independently.

### 2. XmppStanzaDispatcher — Fill Missing Failure Triggers

Two methods currently log and close the channel without updating connection state:

```java
// In exceptionCaught():
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("XMPP channel exception for device {}", connection.getDeviceId(), cause);
    connection.setState(ConnectionState.FAILED);  // NEW
    ctx.close();
}

// In channelInactive():
public void channelInactive(ChannelHandlerContext ctx) {
    log.warn("XMPP channel inactive for device {}", connection.getDeviceId());
    connection.setState(ConnectionState.DISCONNECTED);  // NEW
}
```

### 3. ReconnectScheduler — Core Scheduler

```java
public class ReconnectScheduler {
    private static final long INITIAL_DELAY_MS = 30_000;   // 30 seconds
    private static final long MAX_DELAY_MS = 30 * 60_000;  // 30 minutes

    // Dedup: tracks devices currently waiting for a scheduled reconnect
    private final Set<String> pendingDevices = ConcurrentHashMap.newKeySet();

    // Per-device backoff state
    private final ConcurrentHashMap<String, BackoffTracker> backoffTrackers = new ConcurrentHashMap<>();

    // Scheduler for delayed reconnect execution
    private final ScheduledExecutorService scheduler;

    // Connection provider: lookup XmppConnection by deviceId
    private final Function<String, XmppConnection> connectionProvider;

    // Netty event loop group for new connections
    private final EventLoopGroup eventLoopGroup;

    public ReconnectScheduler(ScheduledExecutorService scheduler,
                               Function<String, XmppConnection> connectionProvider,
                               EventLoopGroup eventLoopGroup) {
        this.scheduler = scheduler;
        this.connectionProvider = connectionProvider;
        this.eventLoopGroup = eventLoopGroup;
    }

    /**
     * Called when a device connection fails. Schedules a reconnect with backoff.
     * If the device is already pending, the request is ignored (dedup).
     */
    public void onFailure(String deviceId) {
        if (!pendingDevices.add(deviceId)) {
            return;  // Already pending — ignore duplicate
        }
        BackoffTracker tracker = backoffTrackers.computeIfAbsent(deviceId, k -> new BackoffTracker());
        long delay = tracker.nextDelay();
        log.info("Scheduling reconnect for device {} in {}ms (attempt {})",
                 deviceId, delay, tracker.getAttemptCount());
        scheduler.schedule(() -> reconnect(deviceId), delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Called when a device comes online. Resets its backoff counter.
     */
    public void onSuccess(String deviceId) {
        BackoffTracker tracker = backoffTrackers.get(deviceId);
        if (tracker != null) {
            tracker.reset();
        }
    }

    /**
     * Execute the reconnect attempt.
     */
    private void reconnect(String deviceId) {
        pendingDevices.remove(deviceId);
        XmppConnection conn = connectionProvider.apply(deviceId);
        if (conn != null) {
            log.info("Attempting reconnect for device {}", deviceId);
            conn.connect(eventLoopGroup);  // Failure will trigger onFailure again → backoff escalation
        } else {
            log.warn("No connection found for device {}, skipping reconnect", deviceId);
        }
    }

    /**
     * Check if a device is currently pending reconnect.
     */
    public boolean isPending(String deviceId) {
        return pendingDevices.contains(deviceId);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        pendingDevices.clear();
    }
}
```

### 4. BackoffTracker — Per-Device Backoff State

Inner class of `ReconnectScheduler`:

```java
class BackoffTracker {
    private int attemptCount = 0;

    /**
     * Calculate next delay using exponential backoff:
     * 30s, 1min, 2min, 4min, 8min, 16min, 30min, 30min, ...
     */
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

    /**
     * Add ±10% random jitter to prevent thundering herd.
     */
    private long addJitter(long delay) {
        long jitter = (long) (ThreadLocalRandom.current().nextDouble(-0.1, 0.1) * delay);
        return Math.max(delay + jitter, INITIAL_DELAY_MS / 2);  // Never go below 15s
    }
}
```

### 5. XmppEngine — Callback Registration

```java
public class XmppEngine {
    private ReconnectScheduler reconnectScheduler;  // New

    public void start(List<XmppConnection> connections) {
        this.reconnectScheduler = new ReconnectScheduler(scheduler, this::getConnection, eventLoopGroup);

        for (XmppConnection conn : connections) {
            addConnection(conn);
        }

        // No healthCheck scheduling needed — reconnect is purely callback-driven.
        // Ping-based keepalive is handled per-connection by PingHandler on its own schedule.
    }

    public void addConnection(XmppConnection conn) {
        this.connections.add(conn);

        // Register scheduler callbacks
        conn.onFailure(reconnectScheduler::onFailure);
        conn.onSuccess(reconnectScheduler::onSuccess);

        // Start initial connection
        conn.connect(eventLoopGroup);
    }

    private XmppConnection getConnection(String deviceId) {
        return connections.stream()
            .filter(c -> c.getDeviceId().equals(deviceId))
            .findFirst().orElse(null);
    }

    // healthCheck() is removed — reconnect is entirely callback-driven.
    // Ping-based keepalive is handled per-connection by PingHandler, not here.

    public void stop() {
        reconnectScheduler.shutdown();
        // ... existing shutdown logic
    }
}
```

### 6. ReconnectPolicy — Deletion

`ReconnectPolicy` is deleted. Its responsibilities are split:
- **Backoff calculation** → `BackoffTracker`
- **Scheduling execution** → `ReconnectScheduler`

The old `ReconnectPolicy` used 1s initial / 60s max delays and mixed calculation with scheduling. The new design separates these concerns with the correct parameters (30s / 30min).

## Failure Trigger Coverage

All failure paths now correctly set connection state and trigger the scheduler callback:

| Scenario | Trigger Location | State Set | Callback Fired |
|---|---|---|---|
| TCP connect failure | `XmppConnection.connect()` | FAILED | Yes (existing) |
| SASL auth failure | `SaslHandler.handleAuthResult()` | FAILED | Yes (existing) |
| Bind failure | `BindHandler.handleBindResult()` | FAILED | Yes (existing) |
| Ping timeout | `PingHandler.sendPing()` | FAILED | Yes (existing) |
| Channel exception | `XmppStanzaDispatcher.exceptionCaught()` | FAILED | Yes (**new**) |
| Channel inactive | `XmppStanzaDispatcher.channelInactive()` | DISCONNECTED | Yes (**new**) |

## Data Flow

```
1. Failure occurs (any trigger above)
   └→ XmppConnection.setState(FAILED/DISCONNECTED)
       ├→ onStateChange → CpeDevice updates DeviceState (unchanged)
       └→ onFailure callback → ReconnectScheduler.onFailure(deviceId)

2. ReconnectScheduler.onFailure(deviceId)
   ├→ pendingDevices.add(deviceId) fails? → duplicate, ignore
   └→ pendingDevices.add(deviceId) succeeds → new entry
       ├→ backoffTrackers.get(deviceId).nextDelay() → calculate delay
       └→ scheduler.schedule(reconnect, delay)

3. Delay expires → ReconnectScheduler.reconnect(deviceId)
   ├→ pendingDevices.remove(deviceId)
   └→ conn.connect(eventLoopGroup)
       ├→ Success → setState(ONLINE) → onSuccess callback → tracker.reset()
       └→ Failure → setState(FAILED) → onFailure callback → backoff escalates
```

## Thread Safety

| Component | Mechanism |
|---|---|
| `pendingDevices` | `ConcurrentHashMap.newKeySet()` — atomic `add()` provides dedup |
| `backoffTrackers` | `ConcurrentHashMap.computeIfAbsent()` — atomic get-or-create |
| `BackoffTracker.nextDelay()/reset()` | Called from `ScheduledExecutorService` tasks — serialized per device by the pending-device dedup |
| `scheduler.schedule()` | Thread-safe by contract |

## Testing Strategy

1. **Unit: BackoffTracker** — verify delay sequence (30s, 1min, 2min, ..., 30min, 30min), reset behavior, jitter bounds
2. **Unit: ReconnectScheduler** — verify dedup (second `onFailure` for same device ignored), backoff escalation across multiple failures, reset on success
3. **Unit: XmppConnection** — verify callbacks fire on FAILED/DISCONNECTED/ONLINE state transitions
4. **Integration: XmppStanzaDispatcher** — verify `exceptionCaught()` and `channelInactive()` set state and trigger callbacks
5. **Integration: end-to-end** — simulate connection failure → verify scheduled reconnect → verify backoff escalation → simulate success → verify backoff reset
