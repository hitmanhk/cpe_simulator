## ADDED Requirements

### Requirement: DataModel root name derivation
DataModel SHALL provide a `getRootName()` method that returns the name of the top-level parameter node beneath the empty root. For TR-181 models this SHALL return `"Device"`, for TR-098 models this SHALL return `"InternetGatewayDevice"`. If the model has zero or more than one top-level node, `getRootName()` SHALL return an empty string.

#### Scenario: TR-181 model root name
- **WHEN** a DataModel is loaded via `ModelLoader.load("tr181")`
- **THEN** `getRootName()` returns `"Device"`

#### Scenario: TR-098 model root name
- **WHEN** a DataModel is loaded via `ModelLoader.load("tr098")`
- **THEN** `getRootName()` returns `"InternetGatewayDevice"`

#### Scenario: Empty model root name
- **WHEN** a DataModel has no children under its root node
- **THEN** `getRootName()` returns `""`

### Requirement: Relative path resolution
DataModel SHALL provide a `resolvePath(String relativePath)` method that combines `getRootName()` with the given relative path to produce an absolute parameter path. When `getRootName()` is non-empty, it SHALL return `getRootName() + "." + relativePath`. When `getRootName()` is empty, it SHALL return `relativePath` unchanged.

#### Scenario: Resolve relative path under TR-181
- **WHEN** `resolvePath("DeviceInfo.Manufacturer")` is called on a TR-181 DataModel
- **THEN** the result is `"Device.DeviceInfo.Manufacturer"`

#### Scenario: Resolve relative path under TR-098
- **WHEN** `resolvePath("DeviceInfo.Manufacturer")` is called on a TR-098 DataModel
- **THEN** the result is `"InternetGatewayDevice.DeviceInfo.Manufacturer"`

#### Scenario: Resolve relative path with empty root name
- **WHEN** `resolvePath("DeviceInfo.Manufacturer")` is called on a DataModel with empty root name
- **THEN** the result is `"DeviceInfo.Manufacturer"` (unchanged)

### Requirement: Relative value getter
DataModel SHALL provide a `getRelativeValue(String relativePath)` method that resolves the relative path to an absolute path using `resolvePath` and then delegates to `getValue(absolutePath)`.

#### Scenario: Get relative value under TR-098
- **WHEN** `getRelativeValue("DeviceInfo.Manufacturer")` is called on a TR-098 DataModel
- **THEN** the result equals `getValue("InternetGatewayDevice.DeviceInfo.Manufacturer")`

#### Scenario: Get relative value that does not exist
- **WHEN** `getRelativeValue("DeviceInfo.NonExistent")` is called
- **THEN** the result is `null`

### Requirement: Relative value setter
DataModel SHALL provide a `setRelativeValue(String relativePath, String value)` method that resolves the relative path to an absolute path using `resolvePath` and then delegates to `setValue(absolutePath, value)`. If the resolved absolute path does not exist in the DataModel, it SHALL throw `IllegalArgumentException`, consistent with `setValue` behavior.

#### Scenario: Set relative value under TR-098
- **WHEN** `setRelativeValue("ManagementServer.ConnectionRequestURL", "http://example.com/connReq")` is called on a TR-098 DataModel
- **THEN** `getValue("InternetGatewayDevice.ManagementServer.ConnectionRequestURL")` returns `"http://example.com/connReq"`

#### Scenario: Set relative value on non-existent path
- **WHEN** `setRelativeValue("ManagementServer.NonExistent", "value")` is called
- **THEN** `IllegalArgumentException` is thrown

### Requirement: Absolute path API unchanged
The existing `getNode(String path)`, `getValue(String path)`, `setValue(String path, String value)`, `getParameterNames(String path, boolean nextLevel)`, `isWritable(String path)` methods SHALL continue to accept absolute parameter paths and behave identically to their current behavior. No automatic relative-path fallback SHALL be added to these methods.

#### Scenario: getValue with absolute path under TR-098
- **WHEN** `getValue("InternetGatewayDevice.DeviceInfo.Manufacturer")` is called on a TR-098 DataModel
- **THEN** the result is the same as before this change

#### Scenario: getValue with wrong-root absolute path returns null
- **WHEN** `getValue("Device.DeviceInfo.Manufacturer")` is called on a TR-098 DataModel
- **THEN** the result is `null` (no automatic fallback)