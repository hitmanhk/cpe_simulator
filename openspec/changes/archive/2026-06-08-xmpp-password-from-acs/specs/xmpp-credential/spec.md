## ADDED Requirements

### Requirement: XMPP Password parameter in DataModel
DataModel SHALL contain a writable `XMPP.Connection.1.Password` parameter node for TR-181 (`Device.XMPP.Connection.1.Password`) and TR-098 (`InternetGatewayDevice.XMPP.Connection.1.Password`). The parameter SHALL have `writable=true`, `notification=2`, type `xsd:string`, and initial value empty string.

#### Scenario: Parameter exists in TR-181 model
- **WHEN** tr181-default.yaml is loaded
- **THEN** DataModel contains `Device.XMPP.Connection.1.Password` with writable=true, notification=2, type xsd:string, value=""

#### Scenario: Parameter exists in TR-098 model
- **WHEN** tr098-default.yaml is loaded
- **THEN** DataModel contains `InternetGatewayDevice.XMPP.Connection.1.Password` with writable=true, notification=2, type xsd:string, value=""

### Requirement: XMPP connection created after ACS sets password
In NAT non-xmpp-only mode, when ACS sends SetParameterValues containing the XMPP Password parameter with a non-empty value, the device SHALL create an XmppConnection and initiate XMPP connection using that password as the SASL authentication password. The XMPP username SHALL remain `deviceId.toLowerCase()`.

#### Scenario: ACS sets XMPP password for device without XMPP connection
- **WHEN** ACS sends SetParameterValues with `Device.XMPP.Connection.1.Password` = "device-secret" and the device has no existing XmppConnection
- **THEN** an XmppConnection is created, xmppPassword is set to "device-secret", and XMPP connection is initiated with username=deviceId.toLowerCase() and password="device-secret"

#### Scenario: XMPP password empty — no connection
- **WHEN** ACS sends SetParameterValues with `Device.XMPP.Connection.1.Password` = "" (empty)
- **THEN** no XmppConnection is created, XMPP connection is not initiated

#### Scenario: XMPP connection already exists — no action on password set
- **WHEN** ACS sends SetParameterValues with `Device.XMPP.Connection.1.Password` and the device already has an XmppConnection
- **THEN** the value is written to DataModel but no XMPP connection action is triggered

### Requirement: XMPP not started at boot in NAT non-xmpp-only mode
In NAT non-xmpp-only mode, SimulationEngine SHALL NOT create XmppConnection instances at startup. XmppEngine SHALL be created and started with an empty connection list, but no XMPP connections are initiated until ACS provides the password.

#### Scenario: NAT mode startup without xmpp-only
- **WHEN** SimulationEngine starts in NAT mode with xmpp-only=false
- **THEN** XmppEngine is created and started with empty connections, no XmppConnection is created for any device, xmppEngine is injected into each CpeDevice

### Requirement: SASL password reads from XmppConnection field
SaslHandler SHALL read the XMPP authentication password from `connection.getXmppPassword()`. When `xmppPassword` is null, SaslHandler SHALL fall back to `config.getPassword()` for backward compatibility.

#### Scenario: xmppPassword is set
- **WHEN** XmppConnection.getXmppPassword() returns a non-null value
- **THEN** SaslHandler uses that value as the SASL authentication password

#### Scenario: xmppPassword is null — fallback
- **WHEN** XmppConnection.getXmppPassword() returns null
- **THEN** SaslHandler falls back to config.getPassword() as the SASL authentication password

### Requirement: XMPP progressive reconnect on failure
When XMPP connection fails or disconnects unexpectedly after being established, the existing healthCheck + ReconnectPolicy mechanism SHALL handle progressive retry with exponential backoff (1s → 60s max).

#### Scenario: XMPP connection fails after initial connect
- **WHEN** an XmppConnection that was established via ACS password provisioning enters FAILED or DISCONNECTED state
- **THEN** XmppEngine.healthCheck detects the failure and schedules reconnect via ReconnectPolicy with progressive backoff

### Requirement: XmppEngine supports adding single connection
XmppEngine SHALL provide an `addConnection(XmppConnection conn)` method that adds a single connection to the internal connections list and initiates its connect using the existing eventLoopGroup.

#### Scenario: Add single XMPP connection to running engine
- **WHEN** addConnection(conn) is called on a running XmppEngine
- **THEN** the connection is added to the connections list and conn.connect(eventLoopGroup) is called