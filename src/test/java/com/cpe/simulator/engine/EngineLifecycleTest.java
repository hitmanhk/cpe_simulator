package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the split engine lifecycle used by the GUI:
 * initialize (no listeners) → startHeadless (infrastructure only) →
 * addDevices (CREATED only) → startDevices (online) → stopDevices (offline).
 */
class EngineLifecycleTest {

    private SimulationEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stopForced();
        }
    }

    private SimulationEngine newEngine() throws Exception {
        AppConfig config = new AppConfig();
        config.setCount(50); // headless 模式必须忽略 count
        config.setAcsUrl("http://127.0.0.1:1/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(freePort());
        config.getInformSchedules().getPeriodic().setInterval(1);
        return new SimulationEngine(config);
    }

    @Test
    void headlessStartsWithEmptyPoolAndIgnoresCount() throws Exception {
        engine = newEngine();
        engine.startHeadless();

        assertTrue(engine.isRunning());
        assertEquals(0, engine.getDeviceManager().getDeviceCount(), "headless 不创建任何设备");
        assertEquals(0, engine.getStatus().totalDevices());
        assertFalse(engine.getVendors().isEmpty(), "默认厂商应存在");
        assertEquals(VendorProfile.DEFAULT_VENDOR_NAME, engine.getVendors().get(0).name());
    }

    @Test
    void addDevicesCreatesCreatedDevicesOnly() throws Exception {
        engine = newEngine();
        engine.startHeadless();

        VendorProfile vendor = new VendorProfile("华为", "Huawei", "48A9D2", "HG8245", null, null);
        engine.registerVendor(vendor);
        List<CpeDevice> created = engine.addDevices("华为", 3, Map.of(
                "DeviceInfo.SoftwareVersion", "2.1.0",
                "DeviceInfo.HardwareVersion", "2.0"));

        assertEquals(3, created.size());
        for (CpeDevice device : created) {
            assertEquals(CpeDevice.DeviceState.CREATED, device.getState(), "新增设备应保持 CREATED，不上报");
            assertEquals("华为", device.getVendorName());
            assertTrue(device.getDeviceId().startsWith("48A9D2-HG8245-"),
                    "DeviceId 应使用厂商 OUI/ProductClass：" + device.getDeviceId());
            assertEquals("Huawei", device.getDataModel().getRelativeValue("DeviceInfo.Manufacturer").getValue());
            assertEquals("2.1.0", device.getDataModel().getRelativeValue("DeviceInfo.SoftwareVersion").getValue());
            assertEquals("2.0", device.getDataModel().getRelativeValue("DeviceInfo.HardwareVersion").getValue());
            // 回连路由可达
            assertNotNull(engine.getDeviceRegistry().getDevice(device.getDeviceId()));
        }
    }

    @Test
    void startAndStopDevicesTransitions() throws Exception {
        engine = newEngine();
        engine.startHeadless();
        List<CpeDevice> created = engine.addDevices(VendorProfile.DEFAULT_VENDOR_NAME, 2, Map.of());
        List<String> ids = created.stream().map(CpeDevice::getDeviceId).toList();

        engine.startDevices(ids);
        for (CpeDevice device : created) {
            assertEquals(CpeDevice.DeviceState.ONLINE, device.getState(), "启动设备后应上线");
            assertNotNull(device.getOnlineSince(), "上线时间戳应记录");
            assertEquals("公网", device.getConnectionType());
        }
        assertEquals(2, engine.getStatus().onlineDevices());

        engine.stopDevices(ids);
        for (CpeDevice device : created) {
            assertEquals(CpeDevice.DeviceState.OFFLINE, device.getState(), "停止设备后应离线");
            assertNull(device.getOnlineSince(), "离线后上线时间戳应清空");
        }
        assertEquals(0, engine.getStatus().onlineDevices());
    }

    @Test
    void startDevicesRequiresRunningEngine() throws Exception {
        engine = newEngine();
        engine.addDevices(VendorProfile.DEFAULT_VENDOR_NAME, 1, Map.of());
        assertThrows(IllegalStateException.class,
                () -> engine.startDevices(List.of("any")), "未启动基础设施时禁止启动设备");
    }

    @Test
    void restoreDeviceWithOriginalSerial() throws Exception {
        engine = newEngine();
        engine.startHeadless();
        VendorProfile vendor = engine.getVendors().get(0);
        List<CpeDevice> created = engine.addDevices(vendor.name(), 2, Map.of());
        String originalId = created.get(0).getDeviceId();
        String originalSerial = created.get(0).getSerialNumber();

        // 模拟引擎重启：新引擎用原序列号重建，deviceId 应一致（ACS 视角身份不变）
        engine.stopForced();
        engine = newEngine();
        engine.startHeadless();
        CpeDevice restored = engine.getDeviceManager().createDeviceWithSerial(
                engine.getVendors().get(0), originalSerial, Map.of());
        assertEquals(originalId, restored.getDeviceId(), "按原序列号重建应保持 deviceId 不变");
        assertEquals(VendorProfile.DEFAULT_VENDOR_NAME, restored.getVendorName());
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
