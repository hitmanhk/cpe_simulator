## Why

When switching the data model to TR-098 (`--model tr098`), the simulator still produces TR-181-style messages. The YAML data model layer correctly loads `InternetGatewayDevice.*` parameter paths, but the business logic layer (InformHandler, DownloadHandler, SimulationEngine) hardcodes `Device.*` absolute paths. This causes Inform messages to contain empty values, firmware upgrade version updates to target wrong nodes, and ConnectionRequestURL assignment to fail. Only `ConnectionRequestIntegration` has a partial fix with an if/else branch, but even that is bypassed by SimulationEngine's own hardcoded setValue.

## What Changes

- Add `getRootName()`, `resolvePath()`, `getRelativeValue()`, and `setRelativeValue()` methods to `DataModel` to support model-aware relative path resolution
- Convert all hardcoded `Device.*` absolute paths in simulator-internal code to relative paths using `resolvePath`
- Eliminate the `if ("tr098".equalsIgnoreCase(...))` branch in `ConnectionRequestIntegration`
- Update InformHandler's `INFORM_PARAMS` array and `DeviceIdStruct` construction to use relative paths
- Update DownloadHandler's software version lookup to use relative paths
- Update SimulationEngine's ConnectionRequestURL assignment to use relative paths

## Capabilities

### New Capabilities
- `relative-path-resolution`: DataModel API for resolving model-relative parameter paths (root-aware path prefixing)

### Modified Capabilities

## Impact

- `com.cpe.simulator.model.DataModel` — new methods added, no breaking changes to existing API
- `com.cpe.simulator.cwmp.InformHandler` — INFORM_PARAMS converted to relative paths, DeviceIdStruct uses getRelativeValue
- `com.cpe.simulator.cwmp.DownloadHandler` — software version lookup uses relative path
- `com.cpe.simulator.engine.SimulationEngine` — ConnectionRequestURL assignment uses setRelativeValue
- `com.cpe.simulator.engine.ConnectionRequestIntegration` — if/else branch eliminated, simplified to setRelativeValue
- Existing tests — DataModelTest and ModelLoaderTest use absolute paths and remain valid; new tests needed for relative path API