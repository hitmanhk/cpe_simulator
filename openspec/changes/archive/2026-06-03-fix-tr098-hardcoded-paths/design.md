## Context

The CPE simulator supports two TR-069 data model standards: TR-181 (root `Device`) and TR-098 (root `InternetGatewayDevice`). The YAML data model layer (`ModelLoader` + `DataModel`) correctly loads the appropriate parameter tree. However, the business logic layer hardcodes `Device.*` absolute paths in multiple places, making TR-098 mode produce messages with empty/wrong values. The current DataModel stores parameters under an empty-name root node with the real model root (`Device` or `InternetGatewayDevice`) as its sole child.

The two data model standards share identical subtree structures beneath their respective roots: `DeviceInfo.Manufacturer`, `ManagementServer.ConnectionRequestURL`, etc. — the only difference is the top-level root name.

Two distinct usage scenarios exist for parameter paths:
- **ACS-driven (transparent)**: RPC handlers receive absolute paths from ACS requests and pass them through DataModel. These paths naturally match the loaded model and require no change.
- **Simulator-driven (active construction)**: InformHandler, DownloadHandler, SimulationEngine, and ConnectionRequestIntegration construct messages using internally chosen parameter paths. These are where the hardcoded `Device.*` paths cause failures under TR-098.

## Goals / Non-Goals

**Goals:**
- Make TR-098 mode produce correct Inform, Download, and ConnectionRequest messages with `InternetGatewayDevice.*` paths and values
- Provide a DataModel API that allows business code to reference parameters without knowing the model root name
- Eliminate all model-specific if/else branches in business logic
- Maintain backward compatibility: existing absolute-path API (`getNode`, `getValue`, `setValue`) unchanged

**Non-Goals:**
- Changing the DataModel root node structure (the empty-name root wrapper stays)
- Modifying ACS-driven RPC handlers (they receive absolute paths and work correctly)
- Supporting YAML files with multiple top-level roots
- Adding automatic path fallback/heuristics in `getValue`/`setValue` (explicit `resolvePath` is cleaner)

## Decisions

### Decision 1: Add relative-path API to DataModel rather than restructuring root node

**Choice**: Add `getRootName()`, `resolvePath()`, `getRelativeValue()`, `setRelativeValue()` methods to the existing DataModel class, keeping the empty-name root node unchanged.

**Alternatives considered**:
- A: Make the real model root (`Device`/`InternetGatewayDevice`) be `DataModel.root` directly — rejected because it changes `getNode("")` semantics, affects `getParameterNames("")` output, and breaks the conceptual model where root is a neutral container
- B: Add `rootName` field to YAML format — rejected because the root name is already implicitly defined by all parameter paths sharing a common prefix, adding an explicit field is redundant and introduces a consistency validation burden
- C: Automatic fallback in `getValue` (try absolute, then try with root prefix) — rejected because it introduces ambiguity: `getValue("DeviceInfo.Manufacturer")` could be a relative lookup or a genuine (but nonexistent) absolute path. Explicit `getRelativeValue` is unambiguous.

**Rationale**: The existing root structure (empty wrapper → single child) is well-established, all tests use it, and ACS-driven paths work through it naturally. Adding methods on top is the least disruptive approach.

### Decision 2: Business code uses relative paths, not model-aware constants

**Choice**: Convert hardcoded absolute paths (`"Device.DeviceInfo.Manufacturer"`) to relative paths (`"DeviceInfo.Manufacturer"`) and call `dm.resolvePath()` or `dm.getRelativeValue()` at runtime.

**Alternatives considered**:
- Keep absolute paths but derive them from config (`config.getModel()` prefix + relative suffix) — rejected because it spreads config dependency everywhere and duplicates the resolvePath logic
- Centralize all path mappings in a single constants class — rejected because it adds an unnecessary indirection layer; the paths are only used in a few specific handlers

**Rationale**: Relative paths are self-documenting and the `resolvePath` call makes the intent explicit. Each handler's parameter list stays local and readable.

## Risks / Trade-offs

- **[Risk] `getRootName()` assumes single-root YAML** → Mitigation: Method returns empty string for multi-root or empty models, causing `resolvePath` to return the relative path unchanged (degrades gracefully to absolute-path lookup). Real TR-069 models always have a single root, so this is a theoretical edge case.
- **[Risk] Relative paths could be confused with absolute paths** → Mitigation: Separate method names (`getRelativeValue` vs `getValue`) make the intent clear. No automatic fallback to avoid ambiguity.
- **[Risk] Inform SOAP output must use absolute paths** → Mitigation: `resolvePath` produces absolute paths suitable for SOAP output. The ParameterInfo name field in Inform will use `dm.resolvePath(rel)` to generate the correct absolute path for both TR-181 and TR-098.