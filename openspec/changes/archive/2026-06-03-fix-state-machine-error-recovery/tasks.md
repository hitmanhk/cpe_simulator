## 1. CwmpSession State Simplification

- [x] 1.1 Change `CwmpSession.State` enum to `IDLE, IN_SESSION` — remove `INFORM_SENT`, `CPE_HOLD`, `ACS_HOLD`
- [x] 1.2 Replace `startSession()` implementation: transition IDLE → IN_SESSION, tolerate already-IN_SESSION (close and restart)
- [x] 1.3 Replace `sessionComplete()` and `reset()` with single `close()` method: transitions to IDLE, tolerates already-IDLE (no-op)
- [x] 1.4 Remove `informSent()`, `informResponseReceived()`, `acsRequestProcessed()`, `sendEmptyRequest()` methods
- [x] 1.5 Remove all `IllegalStateException` throws from remaining methods (`startSession`, `close`)
- [x] 1.6 Keep `touch()` and `isTimedOut()` — `isTimedOut()` returns false when IDLE, true when IN_SESSION and duration exceeds timeout
- [x] 1.7 Update `DeviceRegistryTest` mock: replace `CwmpSession.State.CPE_HOLD` references with `CwmpSession.State.IN_SESSION`

## 2. CpeDevice DeviceState Simplification

- [x] 2.1 Remove `ERROR` from `CpeDevice.DeviceState` enum — only `CREATED, CONNECTING, ONLINE, OFFLINE`
- [x] 2.2 Remove `state = ERROR` assignment in `runInformSession()` 401 handler (line 128) — just close session, device stays ONLINE
- [x] 2.3 Remove `state = ERROR` assignment in `runInformSession()` catch block (line 165) — just close session, device stays ONLINE
- [x] 2.4 Update `triggerInform()`: remove `ERROR` from rejection check — only reject when `OFFLINE`
- [x] 2.5 Update `runInformSession()` catch block: replace `session.reset()` with `session.close()`, remove `state = ERROR`
- [x] 2.6 Update `runInformSession()` InterruptedException catch: replace `session.reset()` with `session.close()`, remove any state change

## 3. Session Fault Response (processAcsRequests)

- [x] 3.1 In `processAcsRequests()`, wrap `handler.handle()` and `soapBuilder.buildResponse()` in try-catch per individual RPC
- [x] 3.2 On exception in handler or builder: build CWMP Fault with code 9002, message "Internal error: <exception message>", cwmpId from request (fallback to IdGenerator.cwmpId() if null)
- [x] 3.3 Send the fault response to ACS via `cwmpClient.sendRequest()`, continue the ACS request loop — do NOT break or throw
- [x] 3.4 Remove `session.acsRequestProcessed()` call — session no longer has this method, lastActivity tracking is handled by `touch()`

## 4. Session Flow Refactoring (runInformSession)

- [x] 4.1 Replace `session.startSession(events)` → `session.startSession(events)` (stays, now transitions to IN_SESSION)
- [x] 4.2 Remove `session.informSent()` call after sending Inform — no longer exists
- [x] 4.3 Remove `session.informResponseReceived(holdRequests)` call — holdRequests becomes local variable for flow control
- [x] 4.4 Simplify session flow: after InformResponse, if `holdRequests` → enter `processAcsRequests()`, else → `sendEmptyRequest()`
- [x] 4.5 Replace `session.sessionComplete()` with `session.close()` at end of runInformSession
- [x] 4.6 In `sendEmptyRequest()`: remove `session.sendEmptyRequest()` call — replace with `session.touch()`; remove `session.acsRequestProcessed()` — replace with `session.touch()`
- [x] 4.7 Update `sendEmptyRequest()` flow: after ACS responds with a request, enter `processAcsRequests()` directly (no session state transitions needed)

## 5. SimulationEngine Connection Request Handler

- [x] 5.1 Update connection request handler lambda: remove `device.getState() == OFFLINE` as the only check (no ERROR state to worry about)
- [x] 5.2 Verify handler returns 503 for OFFLINE, 200 + triggerInform for all other states

## 6. XMPP State Callback

- [x] 6.1 Update XMPP state callback in `SimulationEngine.start()`: change `FAILED → DeviceState.ERROR` to `FAILED → DeviceState.OFFLINE`
- [x] 6.2 Add error logging for XMPP FAILED state transitions

## 7. Test Updates

- [x] 7.1 Update `XmppStateBridgeTest`: replace all `DeviceState.ERROR` references with `DeviceState.OFFLINE`; update `testFailedStateSyncsToDeviceError` to check OFFLINE instead of ERROR
- [x] 7.2 Update `DeviceRegistryTest`: replace `CwmpSession.State.CPE_HOLD` mock returns with `CwmpSession.State.IN_SESSION`
- [x] 7.3 Add new test: CwmpSession IDLE → IN_SESSION → IDLE transitions with no IllegalStateException
- [x] 7.4 Add new test: CwmpSession.close() on already-IDLE session is no-op
- [x] 7.5 Add new test: CpeDevice.triggerInform() only rejects OFFLINE, accepts all other states
- [x] 7.6 Add new test: processAcsRequests catches handler exception and produces CWMP Fault 9002
- [x] 7.7 Run `mvn test` to verify all tests pass