## ADDED Requirements

### Requirement: CwmpSession has two states — IDLE and IN_SESSION
CwmpSession.State SHALL consist of exactly two enum values: IDLE and IN_SESSION. IDLE means no active CWMP session exists. IN_SESSION means an active CWMP session is established with ACS. The previous states INFORM_SENT, CPE_HOLD, and ACS_HOLD SHALL be removed.

#### Scenario: Session starts on Inform
- **WHEN** startSession() is called with a list of events
- **THEN** the session state transitions from IDLE to IN_SESSION, a sessionId is generated, startTime and lastActivity are set, and pendingEvents are stored

#### Scenario: Session closes after completion
- **WHEN** close() is called during an active session
- **THEN** the session state transitions from IN_SESSION to IDLE, and the session is ready for a new session to start

#### Scenario: Session closes on failure
- **WHEN** close() is called after a pre-session failure (e.g., Inform rejected by ACS)
- **THEN** the session state transitions to IDLE regardless of whether it was IN_SESSION or still IDLE, and the device can attempt a new session

### Requirement: CwmpSession transitions are not guarded by IllegalStateException
CwmpSession SHALL NOT throw IllegalStateException on any state transition method. The session SHALL tolerate transition calls regardless of current state — if the session is already in the target state, the call SHALL be a no-op. This eliminates the fragile guard pattern where one wrong call order crashes the session.

#### Scenario: Calling close on an already-IDLE session
- **WHEN** close() is called when the session state is already IDLE
- **THEN** the session remains IDLE (no-op), no exception is thrown

#### Scenario: Calling startSession on an already-IN_SESSION session
- **WHEN** startSession() is called when the session state is IN_SESSION
- **THEN** the session SHALL close the current session and start a new one with the provided events, replacing the previous session data

### Requirement: Hold-requests semantics are internal turn-tracking
The "who holds" semantics from TR-069 (HoldRequests flag in InformResponse) SHALL be handled as internal logic in CpeDevice, not as CwmpSession state transitions. CwmpSession SHALL NOT have separate methods for informSent, informResponseReceived, acsRequestProcessed, or sendEmptyRequest. The session only tracks whether it is active (IN_SESSION) or not (IDLE).

#### Scenario: InformResponse with HoldRequests=true
- **WHEN** ACS sends InformResponse with HoldRequests=true
- **THEN** CpeDevice.runInformSession() enters the ACS request processing loop (processAcsRequests), and the session stays IN_SESSION

#### Scenario: InformResponse with HoldRequests=false
- **WHEN** ACS sends InformResponse with HoldRequests=false
- **THEN** CpeDevice.runInformSession() sends an empty request to ACS to check for pending requests, and the session stays IN_SESSION until ACS responds with 204 or another request

### Requirement: Session timeout detection uses lastActivity
CwmpSession SHALL continue to track lastActivity timestamp. The isTimedOut() method SHALL check if the duration since lastActivity exceeds timeoutSeconds when the session is IN_SESSION. When IDLE, isTimedOut() SHALL return false.

#### Scenario: Active session exceeds timeout
- **WHEN** the session is IN_SESSION and the time since lastActivity exceeds timeoutSeconds
- **THEN** isTimedOut() returns true

#### Scenario: Idle session timeout check
- **WHEN** the session is IDLE
- **THEN** isTimedOut() returns false regardless of time elapsed