## 1. DataModel Relative Path API

- [x] 1.1 Add `getRootName()` method to `DataModel` — returns sole top-level child name, or empty string for zero/multi-root
- [x] 1.2 Add `resolvePath(String relativePath)` method to `DataModel` — combines `getRootName()` + "." + relativePath, returns relativePath unchanged if root name is empty
- [x] 1.3 Add `getRelativeValue(String relativePath)` method to `DataModel` — delegates to `getValue(resolvePath(relativePath))`
- [x] 1.4 Add `setRelativeValue(String relativePath, String value)` method to `DataModel` — delegates to `setValue(resolvePath(relativePath), value)`
- [x] 1.5 Add unit tests for all four new methods in `DataModelTest` covering TR-181, TR-098, and empty-root scenarios

## 2. InformHandler Conversion

- [x] 2.1 Convert `INFORM_PARAMS` array from `Device.*` absolute paths to relative paths (`DeviceInfo.Manufacturer`, `DeviceInfo.ProductClass`, etc.)
- [x] 2.2 Update `buildInform` parameter lookup loop to use `dm.resolvePath(relPath)` for absolute path and `dm.getValue(dm.resolvePath(relPath))` for value retrieval
- [x] 2.3 Update `DeviceIdStruct` construction to use `dm.getRelativeValue("DeviceInfo.Manufacturer")` instead of `getParamValue(dm, "Device.DeviceInfo.Manufacturer")`
- [x] 2.4 Verify `SoapBuilderTest` for Inform still passes (ParameterInfo names are now dynamically absolute, not hardcoded)

## 3. DownloadHandler Conversion

- [x] 3.1 Replace `dm.getValue("Device.DeviceInfo.SoftwareVersion")` with `dm.getRelativeValue("DeviceInfo.SoftwareVersion")` in `DownloadHandler.handle()`

## 4. SimulationEngine Conversion

- [x] 4.1 Replace `device.getDataModel().setValue("Device.ManagementServer.ConnectionRequestURL", connReqUrl)` with `device.getDataModel().setRelativeValue("ManagementServer.ConnectionRequestURL", connReqUrl)` in `SimulationEngine.start()`

## 5. ConnectionRequestIntegration Cleanup

- [x] 5.1 Replace the `if ("tr098".equalsIgnoreCase(config.getModel()))` branch with `device.getDataModel().setRelativeValue("ManagementServer.ConnectionRequestURL", url)` in `configureConnectionRequestUrl()`
- [x] 5.2 Remove the now-unused `config` field and constructor parameter from `ConnectionRequestIntegration` if no other references remain — config still used for getConnReqHost/getConnReqPort and CwmpServer, not removed

## 6. Integration Verification

- [x] 6.1 Run `mvn test` to verify all existing tests pass
- [x] 6.2 Add a TR-098 Inform integration test verifying that Inform messages contain `InternetGatewayDevice.*` absolute paths and non-empty values