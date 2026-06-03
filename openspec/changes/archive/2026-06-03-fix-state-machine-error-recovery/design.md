## Context

The CPE simulator uses two coupled state machines:

1. **CpeDevice.DeviceState**: `CREATED → CONNECTING → ONLINE → OFFLINE → ERROR` — ERROR is a dead end with no recovery path
2. **CwmpSession.State**: `IDLE → INFORM_SENT → CPE_HOLD → ACS_HOLD → CPE_HOLD → ... → IDLE` — strict `IllegalStateException` guards enforce TR-069 "who holds" semantics

When any exception occurs during a CWMP session (RPC handler throws, network timeout, parsing error), the catch block in `CpeDevice.runInformSession()` sets `state = ERROR` and calls `session.reset()`. The device becomes permanently unresponsive: `triggerInform()` silently returns for ERROR devices, the connection request handler returns 200 OK but never fires an Inform, and periodic inform schedules silently fail.

The root problem: exceptions are treated as system-level failures rather than protocol-level events. In TR-069, the CPE should communicate failures to ACS via CWMP Fault SOAP responses, allowing ACS to decide the next action.

## Goals / Non-Goals

**Goals:**
- Devices are always recoverable after any failure — ACS can always trigger a new session via connection request
- Session exceptions become CWMP Fault responses to ACS, not session/device death
- Simplify state machines to their essential semantics: "is there an active session?" and "is the device reachable?"

**Non-Goals:**
- No internal retry logic — the simulator does not retry failed Informs; ACS drives recovery via connection requests or the periodic inform schedule handles it naturally
- No distinction between "retryable" and "fatal" errors — all internal errors produce the same CWMP Fault (9002) to ACS
- No changes to RPC handler interfaces or existing handler implementations — they already produce faults for their own errors
- No changes to the CWMP client/server HTTP transport layer

## Decisions

### D1: CwmpSession.State simplifies to IDLE / IN_SESSION

**Choice**: 2 states instead of 4.

**Rationale**: The current `INFORM_SENT`, `CPE_HOLD`, `ACS_HOLD` states encode TR-069 "who holds" semantics as hard state transitions. This is wrong because:
- Hold semantics are about whose turn it is in the HTTP request/response protocol, not about state machine phases
- The `IllegalStateException` guards on every transition method create fragile code where one wrong call order crashes the session
- The session doesn't need to know "who holds" — the session logic naturally follows the HTTP protocol flow

**Alternative considered**: Keep 4 states but remove guards. Rejected because the states add complexity without value — `INFORM_SENT` is a transient phase that always immediately becomes "holding", and the distinction between CPE_HOLD and ACS_HOLD is just "did we just send or did ACS just send".

**Implementation**: `CwmpSession.startSession()` transitions IDLE → IN_SESSION. `CwmpSession.close()` transitions IN_SESSION → IDLE. All intermediate transition methods (`informSent`, `informResponseReceived`, `acsRequestProcessed`, `sendEmptyRequest`) are removed. The session tracks `lastActivity` for timeout detection only.

### D2: Remove CpeDevice.DeviceState.ERROR

**Choice**: `DeviceState` becomes `CREATED, CONNECTING, ONLINE, OFFLINE` — no ERROR.

**Rationale**: ERROR was a dead end. Once set, the device could never recover. This violated the simulator's purpose — ACS should always be able to manage devices. A device that can't respond to connection requests is not a simulated CPE, it's a bug.

**Alternative considered**: Make ERROR transient with auto-recovery. Rejected because adding recovery logic is more complexity than just removing the state. If the device had a transient error, it's still "online" — it just needs a new session attempt. If it's truly disconnected, it's OFFLINE.

**Implementation**: Remove ERROR from the enum. Remove all `state = ERROR` assignments. Remove the ERROR check in `triggerInform()`. XMPP FAILED callback logs the error and either stays in current state or transitions to OFFLINE if the XMPP connection is truly lost.

### D3: Catch exceptions in processAcsRequests() → CWMP Fault

**Choice**: Wrap individual RPC handler invocations in try-catch. On exception, build a CWMP Fault (9002 "Internal error") and send it to ACS. Continue the session loop.

**Rationale**: The current code lets exceptions propagate up to `runInformSession()` which kills the session and sets ERROR. This is wrong because:
- The session is still valid — ACS has an open HTTP connection waiting for a response
- Not responding to ACS means the HTTP connection hangs or times out on ACS's side
- ACS should receive the fault and decide: try another RPC, or close the session (204)

**Alternative considered**: Catch at `runInformSession()` level and build fault there. Rejected because it's too late — by then the session state is already in an unknown state, and we'd need to figure out what HTTP response to send. Catching per-RPC is precise: we know exactly which request we're responding to and have its `cwmpId`.

**Implementation**: In `processAcsRequests()`, wrap the `handler.handle()` call and the `soapBuilder.buildResponse()` call in try-catch. On exception:
```java
catch (Exception e) {
    String faultCwmpId = request.getCwmpId() != null ? request.getCwmpId() : IdGenerator.cwmpId();
    responseXml = soapBuilder.buildFault(9002, "Internal error: " + e.getMessage(), faultCwmpId);
}
```
Then proceed to send the fault to ACS and continue the loop.

### D4: Pre-session failures close session, device stays ONLINE

**Choice**: When Inform fails (401, network error, bad InformResponse), close session → IDLE, device stays ONLINE.

**Rationale**: These failures happen before we have an established request/response exchange with ACS. We can't send a CWMP Fault because there's no ACS request to respond to. The right thing is to close the session cleanly and leave the device ONLINE so ACS can retry via connection request or the periodic schedule fires again.

**Implementation**: In `runInformSession()`, the catch block becomes:
```java
catch (Exception e) {
    session.close();  // IN_SESSION → IDLE (or stays IDLE if not started)
    // no state = ERROR; device stays ONLINE
}
```
The 401 authentication failure case also removes `state = ERROR` and just closes the session.

## Risks / Trade-offs

**[Session state becomes less visible]** → The 2-state model makes it harder to debug session progress from logs. Mitigation: add detailed logging of protocol-level events (Inform sent, InformResponse received, ACS request received, response sent) without relying on state names.

**[Loose turn-tracking could allow protocol violations]** → Without hard state guards, the session logic could theoretically send requests out of order. Mitigation: the HTTP protocol itself enforces turn-taking — you can't send a request before receiving a response. The session logic is naturally sequential; state guards were redundant enforcement.

**[XMPP FAILED → no ERROR means device appears "online" but can't reach ACS]** → In NAT mode, if XMPP fails, the device has no way to reach ACS. Setting it to ONLINE is misleading. Mitigation: XMPP FAILED callback should set OFFLINE since the transport is truly lost, not ONLINE. The device can reconnect when XMPP recovers.

**[Existing tests may reference ERROR state or old session states]** → Tests that check for ERROR or CPE_HOLD/ACS_HOLD will break. Mitigation: update tests to match the new model as part of implementation.