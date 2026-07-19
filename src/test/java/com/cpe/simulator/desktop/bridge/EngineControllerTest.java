package com.cpe.simulator.desktop.bridge;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies EngineController state transitions against a real SimulationEngine
 * (public mode, random ports, tiny device count) and the config validation
 * rules shared with the CLI start command.
 */
class EngineControllerTest {

    @Test
    void validateRejectsXmppOnlyInPublicMode() {
        AppConfig config = new AppConfig();
        config.setXmppOnly(true);
        assertNotNull(EngineController.validate(config));
    }

    @Test
    void validateRejectsNatModeWithoutXmppPassword() {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.getXmpp().setHost("xmpp.example.com");
        config.getXmpp().setDomain("example.com");
        config.getXmpp().setPassword("");
        assertNotNull(EngineController.validate(config));
    }

    @Test
    void validateRejectsInvalidJitter() {
        AppConfig config = new AppConfig();
        config.getInformSchedules().getPeriodic().setInitialJitter(1.5);
        assertNotNull(EngineController.validate(config));
    }

    @Test
    void validateAcceptsPublicModeDefaults() {
        assertNull(EngineController.validate(new AppConfig()));
    }

    @Test
    void startStopTransitionsThroughRunning() throws Exception {
        AppConfig config = new AppConfig();
        config.setCount(2);
        config.setAcsUrl("http://127.0.0.1:1/"); // 不可达：Inform 失败但引擎应正常启动
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(freePort());
        config.getInformSchedules().getBootstrap().setInterval(0);
        config.getInformSchedules().getBoot().setInterval(0);
        config.getInformSchedules().getPeriodic().setInterval(0);

        EngineController controller = new EngineController();
        List<EngineController.EngineState> transitions = new CopyOnWriteArrayList<>();
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch stoppedLatch = new CountDownLatch(1);
        controller.addStateListener((state, error) -> {
            transitions.add(state);
            if (state == EngineController.EngineState.RUNNING) {
                runningLatch.countDown();
            }
            if (state == EngineController.EngineState.STOPPED) {
                stoppedLatch.countDown();
            }
        });

        assertTrue(controller.start(config));
        assertTrue(runningLatch.await(60, TimeUnit.SECONDS), "引擎应在 60 秒内进入 RUNNING");
        assertEquals(EngineController.EngineState.RUNNING, controller.getState());
        assertNotNull(controller.getStatus());
        assertEquals(0, controller.getStatus().totalDevices(),
                "「启动模拟器」仅启动基础设施，忽略 count 不建设备");
        assertFalse(controller.start(config), "运行中重复启动应被拒绝");

        assertTrue(controller.stop());
        assertTrue(stoppedLatch.await(60, TimeUnit.SECONDS), "引擎应在 60 秒内回到 STOPPED");
        assertEquals(EngineController.EngineState.STOPPED, controller.getState());
        assertNull(controller.getEngine());

        assertTrue(transitions.contains(EngineController.EngineState.STARTING));
        assertTrue(transitions.contains(EngineController.EngineState.RUNNING));
        assertTrue(transitions.contains(EngineController.EngineState.STOPPING));
    }

    @Test
    void addDevicesWorksBeforeSimulatorStarts() throws Exception {
        EngineController controller = new EngineController();
        AppConfig config = baseConfig();
        controller.setPendingConfig(config);

        // 引擎未启动：新增设备应懒初始化（不开端口）并停在 CREATED
        var created = controller.addDevices(com.cpe.simulator.engine.VendorProfile.DEFAULT_VENDOR_NAME,
                2, java.util.Map.of("DeviceInfo.SoftwareVersion", "3.0"));
        assertEquals(2, created.size());
        for (var device : created) {
            assertEquals(com.cpe.simulator.engine.CpeDevice.DeviceState.CREATED, device.getState());
        }
        assertEquals(2, controller.getDeviceSpecs().size());
        assertEquals("3.0", controller.getDeviceSpecs().get(0).deviceInfo()
                .get("DeviceInfo.SoftwareVersion"));
        assertEquals(EngineController.EngineState.STOPPED, controller.getState(),
                "新增设备不应改变模拟器运行状态");

        // 清理引擎资源
        controller.getEngine().stopForced();
    }

    @Test
    void startDevicesThenStopRestoresPoolWithOriginalSerials() throws Exception {
        EngineController controller = new EngineController();
        AppConfig config = baseConfig();
        controller.setPendingConfig(config); // 懒初始化引擎使用测试配置（含随机端口）

        // 启动前新增设备
        var created = controller.addDevices(com.cpe.simulator.engine.VendorProfile.DEFAULT_VENDOR_NAME,
                2, java.util.Map.of());
        List<String> originalIds = created.stream()
                .map(com.cpe.simulator.engine.CpeDevice::getDeviceId).toList();

        // 启动模拟器（引擎已懒初始化，直接 headless 启动）
        CountDownLatch runningLatch = new CountDownLatch(1);
        controller.addStateListener((state, error) -> {
            if (state == EngineController.EngineState.RUNNING) {
                runningLatch.countDown();
            }
        });
        assertTrue(controller.start(config));
        assertTrue(runningLatch.await(60, TimeUnit.SECONDS));
        assertEquals(2, controller.getStatus().totalDevices());

        // 启动设备 → ONLINE
        controller.startDevices(originalIds);
        for (var device : controller.getDevices()) {
            assertEquals(com.cpe.simulator.engine.CpeDevice.DeviceState.ONLINE, device.getState());
        }
        assertEquals(2, controller.getStatus().onlineDevices());

        // 停止模拟器 → 池保留；重启 → 原 deviceId 恢复
        CountDownLatch stoppedLatch = new CountDownLatch(1);
        CountDownLatch runningLatch2 = new CountDownLatch(1);
        controller.addStateListener((state, error) -> {
            if (state == EngineController.EngineState.STOPPED) {
                stoppedLatch.countDown();
            }
        });
        assertTrue(controller.stop());
        assertTrue(stoppedLatch.await(60, TimeUnit.SECONDS));
        assertEquals(2, controller.getDeviceSpecs().size(), "停止后设备规格池应保留");

        controller.addStateListener((state, error) -> {
            if (state == EngineController.EngineState.RUNNING) {
                runningLatch2.countDown();
            }
        });
        assertTrue(controller.start(config));
        assertTrue(runningLatch2.await(60, TimeUnit.SECONDS));
        List<String> restoredIds = controller.getDevices().stream()
                .map(com.cpe.simulator.engine.CpeDevice::getDeviceId).toList();
        assertEquals(originalIds, restoredIds, "重启后应按原序列号恢复，deviceId 不变");
        for (var device : controller.getDevices()) {
            assertEquals(com.cpe.simulator.engine.CpeDevice.DeviceState.CREATED,
                    device.getState(), "恢复的设备回到 CREATED，需重新「启动设备」");
        }

        CountDownLatch stoppedLatch2 = new CountDownLatch(1);
        controller.addStateListener((state, error) -> {
            if (state == EngineController.EngineState.STOPPED) {
                stoppedLatch2.countDown();
            }
        });
        controller.stop();
        assertTrue(stoppedLatch2.await(60, TimeUnit.SECONDS));
    }

    @Test
    void removeVendorGuards() throws Exception {
        EngineController controller = new EngineController();
        assertNotNull(controller.removeVendor(
                com.cpe.simulator.engine.VendorProfile.DEFAULT_VENDOR_NAME),
                "默认厂商不可删除");

        controller.addVendor(new com.cpe.simulator.engine.VendorProfile(
                "中兴", "ZTE", "B4A1B2", "F660", null, null));
        controller.setPendingConfig(baseConfig());
        controller.addDevices("中兴", 1, java.util.Map.of());
        assertNotNull(controller.removeVendor("中兴"), "有设备的厂商不可删除");
        controller.removeDevices(controller.getDeviceSpecs().stream()
                .map(EngineController.DeviceSpec::deviceId).toList());
        assertNull(controller.removeVendor("中兴"), "空厂商可删除");

        if (controller.getEngine() != null) {
            controller.getEngine().stopForced();
        }
    }

    private static AppConfig baseConfig() throws Exception {
        AppConfig config = new AppConfig();
        config.setAcsUrl("http://127.0.0.1:1/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(freePort());
        config.getInformSchedules().getBootstrap().setInterval(0);
        config.getInformSchedules().getBoot().setInterval(0);
        config.getInformSchedules().getPeriodic().setInterval(0);
        return config;
    }

    @Test
    void stopWhenStoppedReturnsFalse() {
        assertFalse(new EngineController().stop());
    }

    @Test
    void preflightPortPassesOnFreePort() throws Exception {
        AppConfig config = new AppConfig();
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(freePort());
        assertNull(EngineController.preflightPort(config));
    }

    @Test
    void preflightPortFailsOnOccupiedPort() throws Exception {
        try (java.net.ServerSocket blocker = new java.net.ServerSocket(0,
                50, java.net.InetAddress.getByName("127.0.0.1"))) {
            AppConfig config = new AppConfig();
            config.setConnReqHost("127.0.0.1");
            config.setConnReqPort(blocker.getLocalPort());
            String error = EngineController.preflightPort(config);
            assertNotNull(error);
            assertTrue(error.contains(String.valueOf(blocker.getLocalPort())));
            assertTrue(error.contains("conn-req-port"));
        }
    }

    @Test
    void preflightPortSkipsXmppOnly() throws Exception {
        try (java.net.ServerSocket blocker = new java.net.ServerSocket(0,
                50, java.net.InetAddress.getByName("127.0.0.1"))) {
            AppConfig config = new AppConfig();
            config.setMode("nat");
            config.setXmppOnly(true);
            config.setConnReqHost("127.0.0.1");
            config.setConnReqPort(blocker.getLocalPort());
            assertNull(EngineController.preflightPort(config), "xmpp-only 不启动 CwmpServer，应跳过预检");
        }
    }

    @Test
    void friendlyErrorExplainsBindFailure() {
        AppConfig config = new AppConfig();
        config.setConnReqPort(7547);
        Exception wrapped = new RuntimeException("start failed",
                new java.net.BindException("Address already in use: bind"));
        String message = EngineController.friendlyError(wrapped, config);
        assertTrue(message.contains("7547"));
        assertTrue(message.contains("conn-req-port"));
        assertTrue(message.contains("保留端口段"));
    }

    @Test
    void friendlyErrorFallsBackToMessage() {
        AppConfig config = new AppConfig();
        Exception t = new IllegalStateException("something else");
        assertEquals("something else", EngineController.friendlyError(t, config));
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
