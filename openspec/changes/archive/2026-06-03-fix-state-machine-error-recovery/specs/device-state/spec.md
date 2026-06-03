## ADDED Requirements

### Requirement: CpeDevice.DeviceState has no ERROR state
CpeDevice.DeviceState SHALL consist of four enum values: CREATED, CONNECTING, ONLINE, and OFFLINE. The ERROR state SHALL be removed. A device is either reachable (ONLINE) or not reachable (OFFLINE). Internal failures during CWMP sessions SHALL NOT change the device state — they SHALL be handled at the protocol level via CWMP Fault responses.

#### Scenario: Device starts in CREATED state
- **WHEN** a CpeDevice is constructed
- **THEN** its DeviceState is CREATED

#### Scenario: Device transitions to ONLINE after connecting
- **WHEN** the device's connect() method is called in public mode
- **THEN** the device transitions to ONLINE directly (no CONNECTING in public mode)

#### Scenario: Device transitions to OFFLINE on disconnect
- **WHEN** the device's disconnect() method is called
- **THEN** the device transitions to OFFLINE and XMPP connection is disconnected if present

#### Scenario: No state transition on session exception
- **WHEN** an exception occurs during a CWMP session (handler exception, network error during session)
- **THEN** the device state SHALL NOT change — it stays ONLINE (or whatever state it was in before the session attempt)

### Requirement: triggerInform rejects only OFFLINE devices
CpeDevice.triggerInform() SHALL only reject inform triggers when the device state is OFFLINE. The previous rejection of ERROR state SHALL be removed. For any other state (CREATED, CONNECTING, ONLINE), triggerInform() SHALL schedule a new inform session.

#### Scenario: triggerInform on ONLINE device
- **WHEN** triggerInform() is called on a device in ONLINE state
- **THEN** a new inform session is scheduled on the scheduler, and the device attempts to connect to ACS

#### Scenario: triggerInform on OFFLINE device
- **WHEN** triggerInform() is called on a device in OFFLINE state
- **THEN** the inform is rejected with a log message, and no session is scheduled

#### Scenario: triggerInform on CREATED or CONNECTING device
- **WHEN** triggerInform() is called on a device in CREATED or CONNECTING state
- **THEN** a new inform session is scheduled (the device may be in early startup phases)

### Requirement: Connection request handler only checks OFFLINE state
The ACS connection request handler in SimulationEngine SHALL only return 503 (Service Unavailable) when the device is OFFLINE. For all other states (CREATED, CONNECTING, ONLINE), the handler SHALL accept the request, call triggerInform("6 CONNECTION REQUEST"), and return 200 OK.

#### Scenario: Connection request for ONLINE device
- **WHEN** ACS sends a connection request for a device in ONLINE state
- **THEN** the handler returns 200 OK and calls triggerInform("6 CONNECTION REQUEST")

#### Scenario: Connection request for OFFLINE device
- **WHEN** ACS sends a connection request for a device in OFFLINE state
- **THEN** the handler returns 503 "Device offline"

#### Scenario: Connection request for CREATED device
- **WHEN** ACS sends a connection request for a device in CREATED state (not yet initialized)
- **THEN** the handler returns 200 OK and calls triggerInform("6 CONNECTION REQUEST")

### Requirement: XMPP FAILED callback does not set ERROR state
When an XMPP connection transitions to FAILED state, the callback SHALL NOT set the device to ERROR. Instead, the callback SHALL set the device to OFFLINE (since the XMPP transport is truly lost and the device cannot reach ACS without it). The error SHALL be logged.

#### Scenario: XMPP connection fails in NAT mode
- **WHEN** the XMPP connection state changes to FAILED
- **THEN** the device transitions to OFFLINE, and the error is logged

#### Scenario: XMPP connection reconnects after failure
- **WHEN** the XMPP connection state changes to ONLINE after a previous FAILED
- **THEN** the device transitions back to ONLINE