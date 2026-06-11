# AddObject Realistic Simulation Design

## Problem

`AddObjectHandler` calls `DataModel.addInstance()`, which creates an **empty shell** `ParameterNode` — no child parameters are cloned from the template instance (instance 1). This means:

- `AddObject Device.WANDevice.` creates `Device.WANDevice.2` with no children
- `GetParameterValues Device.WANDevice.2.` returns nothing
- `SetParameterValues` on any child path of the new instance fails with "Parameter not found"
- The ACS cannot meaningfully interact with newly added instances

## Requirements

1. **Clone instance 1's subtree** when adding a new instance — all child parameters with their values, types, writability, and notification settings
2. **Full independence** — each cloned instance has its own deep-copied data; `SetParameterValues` on one instance does not affect another
3. **Reuse deleted instance numbers** — after `DeleteObject Device.WANDevice.2`, a subsequent `AddObject` can return instance 2 again
4. **Multi-level nested AddObject** — the clone logic works at any depth (WANDevice → WANConnectionDevice → WANIPConnection)

## Design

### 1. Clone Instance 1 Subtree in `addInstance()`

When `addInstance(parentPath)` is called:

1. Resolve the parent node (e.g., `WANDevice`)
2. Find instance 1 under the parent (`parent.getChild("1")`)
3. If instance 1 exists: `deepCopy()` its entire subtree
4. Rewrite all `fullPath` values in the deep copy: replace `{parentFullPath}.1.xxx` with `{parentFullPath}.{newInstanceNum}.xxx`
5. Attach the rewritten children to the new instance node
6. If instance 1 does not exist: create the new instance as a bare node (same as current behavior, for graceful degradation)

### 2. Instance Number Reuse

**`ParameterNode`** gains a `TreeSet<Integer> availableInstanceNumbers` field.

- **DeleteObject**: after removing the instance, parse its number and add it to `parent.availableInstanceNumbers`
- **AddObject**: before incrementing `nextInstanceNumber`, check if `availableInstanceNumbers` has a value; if so, `pollFirst()` from it

Example sequence:
```
AddObject WANDevice.  →  WANDevice.2  (nextInstance=3)
DeleteObject WANDevice.2  →  available={2}
AddObject WANDevice.  →  WANDevice.2  (reuse, available={})
AddObject WANDevice.  →  WANDevice.3  (nextInstance=4)
```

### 3. fullPath Rewrite on Clone

`deepCopy()` preserves the original `fullPath` values. After cloning instance 1's subtree for instance N, we must rewrite every node's `fullPath`:

```
Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name
→ Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name
```

Only the **first** instance number segment under the parent changes. Inner instance numbers (e.g., the `1` in `WANConnectionDevice.1`) remain as-is because they belong to a different multiInstance level.

Implementation: add a `rewriteInstancePaths(ParameterNode node, String oldPrefix, String newPrefix)` method to `DataModel` that traverses the subtree and replaces the prefix of every `fullPath`.

### 4. Multi-Level Nested AddObject

No special handling needed. When cloning instance 1's subtree:
- Inner multiInstance containers (e.g., `WANConnectionDevice`) are cloned with their `nextInstanceNumber` intact
- Subsequent `AddObject` on those inner containers works the same way — finds its instance 1 and clones it

## Changes

| File | Change |
|------|--------|
| `ParameterNode` | Add `TreeSet<Integer> availableInstanceNumbers` field; add `addAvailableInstanceNumber(int)` / `pollAvailableInstanceNumber()` methods; update `deepCopy()` to copy the set |
| `DataModel.addInstance()` | Clone instance 1 subtree → rewrite paths → attach to new instance; reuse available instance numbers |
| `DataModel.deleteInstance()` | After removing child, add instance number to `parent.availableInstanceNumbers` |
| `DataModel` | Add `rewriteInstancePaths()` private helper |

## What Does NOT Change

- `AddObjectHandler` — no changes needed
- `DeleteObjectHandler` — no changes needed (logic moves into `DataModel`)
- `ModelLoader` — no changes needed
- YAML model files — no changes needed
- `ParameterValue` — no changes needed
