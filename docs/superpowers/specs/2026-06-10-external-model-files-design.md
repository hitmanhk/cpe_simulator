# External Model Files Design

## Problem

TR-098 and TR-181 model YAML files are embedded inside the JAR (`src/main/resources/models/`). Modifying them requires rebuilding the JAR, which is inconvenient for operational tuning.

## Decision

Move model YAML files out of the JAR into a `models/` directory next to the JAR file. Shortcut names `tr181`/`tr098` resolve to files in this external directory. If files are missing, the application exits with an error — no fallback to JAR-embedded defaults.

## File Location Strategy

`ModelLoader` resolves a base directory to locate the `models/` folder:

1. Obtain the JAR path via `ModelLoader.class.getProtectionDomain().getCodeSource().getLocation()`
2. If the location is a JAR file → baseDir = JAR's parent directory
3. If the location is a directory (IDE/development run) → baseDir = `System.getProperty("user.dir")` (project root)
4. Shortcut mapping: `tr181` → `{baseDir}/models/tr181.yaml`, `tr098` → `{baseDir}/models/tr098.yaml`
5. Non-shortcut values (file paths) are used directly, unchanged

## ModelLoader Changes

- Remove `loadFromClasspath(String resource)` method entirely
- Modify `load(String modelSpec)`:
  - Shortcut names → call `loadFromFile` with `{baseDir}/models/{name}.yaml`
  - Other values → call `loadFromFile` with the passed path directly
  - File not found → throw `RuntimeException` with message: `"Model file not found: {absolutePath}. Expected models/tr181.yaml or models/tr098.yaml in the same directory as the JAR file."`
- Add `resolveBaseDir()` private method to determine baseDir per the strategy above
- `loadFromFile` method remains unchanged

## Resource File Migration

- Move `src/main/resources/models/tr181-default.yaml` → `models/tr181.yaml` (project root)
- Move `src/main/resources/models/tr098-default.yaml` → `models/tr098.yaml` (project root)
- Remove `src/main/resources/models/` directory entirely
- Maven packaging: configure assembly plugin or equivalent so build output includes `models/` directory alongside `cpe-simulator.jar`

## CLI Changes

- `StartCommand` `--model` option description updated to: `"Data model: tr181, tr098, or file path. Shortcut names resolve to models/ directory next to the JAR"`
- `AppConfig` `model` field default value stays `"tr181"` — no change needed
- `ConfigLoader` unaffected (only loads `application.yaml`, not model files)

## Error Handling

- Model file not found: `RuntimeException("Model file not found: {absolutePath}. Expected models/tr181.yaml or models/tr098.yaml in the same directory as the JAR file.")`
- `resolveBaseDir()` failure (rare): exception with message advising to check the runtime environment

## Test Adjustments

- `ModelLoaderTest` — existing `ModelLoader.load("tr181")`/`ModelLoader.load("tr098")` calls work without changes since `user.dir` fallback finds `models/` at project root
- `FatJarVerificationTest` — remove classpath resource assertions for `models/tr181-default.yaml` and `models/tr098-default.yaml`; replace with verification that `ModelLoader.load("tr181")` and `ModelLoader.load("tr098")` succeed
- Other tests (`XmppCredentialTest`, `Tr098InformTest`, `MemoryEstimationTest`) — no changes needed, same reasoning as `ModelLoaderTest`