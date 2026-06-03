## Why

The CWMP session state machine and device state machine are coupled in a way that makes any exception fatal. Once an RPC handler throws or the network blips, the device enters a permanent ERROR state — the session resets, but triggerInform() silently returns for ERROR devices, and the connection request handler returns 200 OK without actually triggering an Inform. The device is dead and ACS cannot recover it. The root cause is treating exceptions as system failures instead of protocol-level communication.

## What Changes

- Simplify `CwmpSession.State` from 4 states (`IDLE, INFORM_SENT, CPE_HOLD, ACS_HOLD`) to 2 states (`IDLE, IN_SESSION`). Remove all `IllegalStateException` guards — the "who holds" semantics are turn-tracking in the session logic, not enforced state transitions.
- Remove `CpeDevice.DeviceState.ERROR`. Devices are either online or offline, never broken. Exceptions during a session become CWMP Fault responses to ACS, not device death.
- Catch RPC handler exceptions locally in `processAcsRequests()`, build a CWMP Fault (9002 "Internal error"), send it to ACS, and continue the session loop. ACS decides whether to close or continue.
- Pre-session failures (Inform returns 401/500, network timeout) close the session back to IDLE and leave the device ONLINE — ACS can retry via connection request, periodic inform retries on the next cycle.
- Fix connection request handler: since ERROR state no longer exists, it only needs to check OFFLINE.
- Fix XMPP FAILED callback: log the error, don't set device to ERROR.

## Capabilities

### New Capabilities
- `session-fault-response`: Ability to catch session-level exceptions and convert them to CWMP Fault SOAP responses sent to ACS, keeping the session alive for ACS to decide next action.

### Modified Capabilities
- `cwmp-session-state`: Session state machine simplified from 4 states to 2 (IDLE / IN_SESSION). Hold-requests semantics handled as internal turn-tracking, not hard state guards.
- `device-state`: Device state machine simplified by removing ERROR state. Devices are always recoverable.

## Impact

- `CwmpSession.java` — State enum, all transition methods, remove IllegalStateException guards
- `CpeDevice.java` — DeviceState enum, runInformSession(), processAcsRequests(), sendEmptyRequest(), triggerInform(), exception handling
- `SimulationEngine.java` — Connection request handler (remove ERROR check), XMPP state callback (remove ERROR mapping)
- `CwmpServerHandler.java` — No changes needed (handler already correct once ERROR is removed)
- `InformScheduler.java` — No changes needed (triggerInform will work for all non-OFFLINE devices)
- Existing RPC handlers already produce CWMP faults for their own exceptions (9003 etc.) — no changes needed to handler logic