## ADDED Requirements

### Requirement: RPC handler exceptions produce CWMP Fault responses
When an RpcHandler throws an exception during a CWMP session, the session logic SHALL catch the exception and convert it into a CWMP Fault SOAP response with fault code 9002 ("Internal error") and the exception message as the fault string. The fault SHALL include the cwmpId from the original ACS request. The session SHALL NOT be terminated by the exception — the fault response SHALL be sent to ACS and the session loop SHALL continue.

#### Scenario: Handler throws during ACS request processing
- **WHEN** ACS sends a GetParameterValues request and the handler throws a runtime exception
- **THEN** the CPE responds with a SOAP Fault containing FaultCode 9002, FaultString "Internal error: <exception message>", and the cwmpId from the ACS request, and the session remains active for ACS to send further requests or close (204)

#### Scenario: Handler throws with null cwmpId
- **WHEN** ACS sends a request with no cwmpId and the handler throws an exception
- **THEN** the CPE generates a new cwmpId (via IdGenerator.cwmpId()), builds a Fault with FaultCode 9002, and sends it to ACS, and the session remains active

#### Scenario: Fault response sent successfully and ACS continues session
- **WHEN** a Fault response is sent to ACS for a handler exception and ACS responds with 200 and another RPC request
- **THEN** the session processes the next ACS request normally — the previous fault does not affect subsequent RPC handling

#### Scenario: Fault response sent and ACS closes session
- **WHEN** a Fault response is sent to ACS and ACS responds with 204 (No Content)
- **THEN** the session closes and transitions to IDLE

### Requirement: SoapBuilder exception produces CWMP Fault response
When SoapBuilder.buildResponse() throws an exception (e.g., malformed envelope data), the session logic SHALL catch it and produce a CWMP Fault response with fault code 9002. This ensures ACS always receives a valid SOAP response to its request.

#### Scenario: SoapBuilder fails to serialize handler response
- **WHEN** an RpcHandler returns a valid SoapEnvelope but SoapBuilder.buildResponse() throws an exception during XML construction
- **THEN** the session catches the exception, builds a Fault(9002, "Internal error: <message>", cwmpId), and sends it to ACS instead, and the session remains active

### Requirement: Pre-session failures do not produce CWMP Faults
When a failure occurs before a valid HTTP request/response exchange is established with ACS (e.g., Inform fails with 401, network timeout, ACS returns non-200), the session SHALL close cleanly to IDLE. No CWMP Fault SHALL be sent because there is no ACS request context to respond to. The device SHALL remain in its current state (ONLINE or CONNECTING).

#### Scenario: Inform receives 401 authentication failure
- **WHEN** the CPE sends an Inform and ACS responds with HTTP 401
- **THEN** the session closes to IDLE, the device stays ONLINE (no ERROR state), and ACS can trigger a new session via connection request

#### Scenario: Inform receives non-200 status code
- **WHEN** the CPE sends an Inform and ACS responds with HTTP 500
- **THEN** the session closes to IDLE, the device stays ONLINE, and the periodic inform schedule will retry on the next cycle

#### Scenario: Network timeout during Inform
- **WHEN** the HTTP client times out while sending an Inform to ACS
- **THEN** the session closes to IDLE, the device stays ONLINE, and ACS can retry via connection request