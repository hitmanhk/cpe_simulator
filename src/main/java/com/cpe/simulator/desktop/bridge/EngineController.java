package com.cpe.simulator.desktop.bridge;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConfigLoader;
import com.cpe.simulator.common.ConnectionRequestEndpoint;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.SimulationEngine;
import com.cpe.simulator.engine.VendorProfile;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controls the {@link SimulationEngine} lifecycle for the desktop UI and owns
 * the persistent vendor/device pools. Pure Java (no JavaFX dependency) so it
 * is unit-testable; listeners are notified on background threads and must
 * marshal to the FX thread themselves.
 * <p>
 * GUI lifecycle model:
 * <ul>
 *   <li>「新增厂商/新增设备」— works before the simulator starts; lazily
 *       initializes the engine core (model loading only, no listeners).</li>
 *   <li>「启动模拟器」— starts infrastructure only ({@code startHeadless}).</li>
 *   <li>「启动设备/停止设备」— per-device online/offline transitions.</li>
 *   <li>Engine stop discards the engine object; the pools here survive and
 *       devices are rebuilt with their original serials on the next start.</li>
 * </ul>
 */
public class EngineController {

    public enum EngineState { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    public interface StateListener {
        /** Invoked on a background (virtual) thread. */
        void onStateChanged(EngineState state, String errorMessage);
    }

    /** Persistent definition of one pooled device (survives engine restarts). */
    public record DeviceSpec(String deviceId, String vendorName, String serialNumber,
                             Map<String, String> deviceInfo) {
    }

    private final AtomicReference<EngineState> state = new AtomicReference<>(EngineState.STOPPED);
    private final CopyOnWriteArrayList<StateListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VendorProfile> vendors = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DeviceSpec> deviceSpecs = new CopyOnWriteArrayList<>();
    private final Map<String, DeviceSpec> specsByDeviceId = new ConcurrentHashMap<>();

    private volatile SimulationEngine engine;
    private volatile AppConfig pendingConfig;
    private volatile String lastError;

    public EngineController() {
        this.pendingConfig = ConfigLoader.loadDefault();
        this.vendors.add(VendorProfile.defaultVendor(pendingConfig));
    }

    // ---------------- 状态与监听 ----------------

    public EngineState getState() {
        return state.get();
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    public String getLastError() {
        return lastError;
    }

    public void addStateListener(StateListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeStateListener(StateListener listener) {
        listeners.remove(listener);
    }

    // ---------------- 配置 ----------------

    /** Called by the UI whenever the form config should become the pending one. */
    public void setPendingConfig(AppConfig config) {
        this.pendingConfig = config;
        // 默认厂商跟随配置更新（用户未显式编辑过时）
        vendors.removeIf(v -> VendorProfile.DEFAULT_VENDOR_NAME.equals(v.name()));
        vendors.add(0, VendorProfile.defaultVendor(config));
    }

    public AppConfig getPendingConfig() {
        return pendingConfig;
    }

    /**
     * Validates the config with the same rules as the CLI start command.
     *
     * @return null when valid, otherwise a human-readable error message
     */
    public static String validate(AppConfig config) {
        if (config.isXmppOnly() && !config.isNatMode()) {
            return "仅 XMPP 模式（xmpp-only）仅在 NAT 模式下可用";
        }
        if (config.isNatMode()) {
            if (config.getXmpp().getHost() == null || config.getXmpp().getHost().isEmpty()) {
                return "NAT 模式必须配置 XMPP 服务器地址（xmpp.host）";
            }
            if (config.getXmpp().getDomain() == null || config.getXmpp().getDomain().isEmpty()) {
                return "NAT 模式必须配置 XMPP 域（xmpp.domain）";
            }
            if (config.getXmpp().getPassword() == null || config.getXmpp().getPassword().isEmpty()) {
                return "NAT 模式必须配置 XMPP 密码（xmpp.password）";
            }
        }
        try {
            config.validateInformSchedules();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    // ---------------- 厂商池 ----------------

    public List<VendorProfile> getVendors() {
        return List.copyOf(vendors);
    }

    public synchronized void addVendor(VendorProfile vendor) {
        vendors.removeIf(v -> v.name().equals(vendor.name()));
        vendors.add(vendor);
        SimulationEngine e = engine;
        if (e != null) {
            e.registerVendor(vendor);
        }
    }

    /**
     * 删除厂商；默认厂商或其下仍有设备时拒绝。
     *
     * @return null 成功，否则拒绝原因
     */
    public synchronized String removeVendor(String name) {
        if (VendorProfile.DEFAULT_VENDOR_NAME.equals(name)) {
            return "默认厂商不可删除";
        }
        boolean hasDevices = deviceSpecs.stream().anyMatch(s -> s.vendorName().equals(name));
        if (hasDevices) {
            return "厂商下仍有设备，请先移除该厂商的全部设备";
        }
        vendors.removeIf(v -> v.name().equals(name));
        SimulationEngine e = engine;
        if (e != null && e.getDeviceManager() != null) {
            e.getDeviceManager().removeVendor(name);
        }
        return null;
    }

    // ---------------- 设备池（新增/移除，未启动也可用） ----------------

    /**
     * Creates devices under the vendor in CREATED state. Lazily initializes
     * the engine core when needed (no network listeners are opened).
     */
    public List<CpeDevice> addDevices(String vendorName, int count, Map<String, String> deviceInfo) {
        SimulationEngine e = ensureEngineInitialized();
        List<CpeDevice> created = e.addDevices(vendorName, count, deviceInfo);
        for (CpeDevice device : created) {
            DeviceSpec spec = new DeviceSpec(device.getDeviceId(), vendorName,
                    device.getSerialNumber(), deviceInfo != null ? Map.copyOf(deviceInfo) : Map.of());
            deviceSpecs.add(spec);
            specsByDeviceId.put(spec.deviceId(), spec);
        }
        return created;
    }

    /** Removes devices from pool and engine. */
    public void removeDevices(Collection<String> deviceIds) {
        SimulationEngine e = engine;
        for (String deviceId : deviceIds) {
            if (e != null) {
                e.stopDevices(List.of(deviceId));
                if (e.getDeviceManager() != null) {
                    e.getDeviceManager().removeDevice(deviceId);
                }
                if (e.getDeviceRegistry() != null) {
                    e.getDeviceRegistry().unregister(deviceId);
                }
            }
            deviceSpecs.removeIf(s -> s.deviceId().equals(deviceId));
            specsByDeviceId.remove(deviceId);
        }
    }

    public List<DeviceSpec> getDeviceSpecs() {
        return List.copyOf(deviceSpecs);
    }

    /** Convenience accessors used by views. */
    public List<CpeDevice> getDevices() {
        SimulationEngine e = engine;
        return e != null && e.getDeviceManager() != null
                ? e.getDeviceManager().getDevices() : List.of();
    }

    public SimulationEngine.SimulationStatus getStatus() {
        SimulationEngine e = engine;
        return e != null ? e.getStatus() : null;
    }

    // ---------------- 模拟器启停（基础设施） ----------------

    /** 「启动模拟器」：仅启动基础设施（回连监听/XMPP 引擎/调度器），异步执行。 */
    public boolean start(AppConfig config) {
        if (!state.compareAndSet(EngineState.STOPPED, EngineState.STARTING)
                && !state.compareAndSet(EngineState.FAILED, EngineState.STARTING)) {
            return false;
        }
        setPendingConfig(config);
        lastError = null;
        notifyListeners(EngineState.STARTING, null);
        Thread.ofVirtual().name("engine-controller-start").start(() -> {
            // 预检回连端口，避免初始化完成后才发现端口不可绑定
            String portError = preflightPort(config);
            if (portError != null) {
                lastError = portError;
                SimLogger.error("Simulator infrastructure start aborted: " + portError);
                state.set(EngineState.FAILED);
                notifyListeners(EngineState.FAILED, lastError);
                return;
            }
            try {
                SimulationEngine e = engine;
                if (e == null) {
                    // 引擎曾被停止：新建并按池恢复设备（原序列号，身份不变）
                    e = new SimulationEngine(config);
                    restorePool(e);
                }
                e.startHeadless();
                engine = e;
                state.set(EngineState.RUNNING);
                notifyListeners(EngineState.RUNNING, null);
            } catch (Throwable t) {
                SimLogger.error("Simulator infrastructure start failed", t);
                lastError = friendlyError(t, config);
                state.set(EngineState.FAILED);
                notifyListeners(EngineState.FAILED, lastError);
            }
        });
        return true;
    }

    /** 「停止模拟器」：停止基础设施；设备规格池保留，重启后按原序列号恢复。 */
    public boolean stop() {
        EngineState current = state.get();
        if (current != EngineState.RUNNING && current != EngineState.FAILED) {
            return false;
        }
        if (!state.compareAndSet(current, EngineState.STOPPING)) {
            return false;
        }
        notifyListeners(EngineState.STOPPING, null);
        Thread.ofVirtual().name("engine-controller-stop").start(() -> {
            try {
                SimulationEngine e = engine;
                if (e != null) {
                    e.stop();
                }
            } catch (Throwable t) {
                lastError = t.getMessage() != null ? t.getMessage() : t.toString();
            } finally {
                engine = null;
                state.set(EngineState.STOPPED);
                notifyListeners(EngineState.STOPPED, lastError);
            }
        });
        return true;
    }

    // ---------------- 设备启停（上报/建联） ----------------

    /** 「启动设备」：勾选设备上线并开始上报/建联（需模拟器已启动）。 */
    public void startDevices(Collection<String> deviceIds) {
        SimulationEngine e = engine;
        if (e == null) {
            throw new IllegalStateException("模拟器未启动，请先点击「启动模拟器」");
        }
        e.startDevices(deviceIds);
    }

    /** 「停止设备」：勾选设备下线。 */
    public void stopDevices(Collection<String> deviceIds) {
        SimulationEngine e = engine;
        if (e != null) {
            e.stopDevices(deviceIds);
        }
    }

    // ---------------- 内部 ----------------

    /** 确保引擎核心已初始化（仅加载模型，不开监听），供启动前新增设备。 */
    private synchronized SimulationEngine ensureEngineInitialized() {
        SimulationEngine e = engine;
        if (e == null) {
            e = new SimulationEngine(pendingConfig);
            restorePool(e);
            engine = e;
        }
        return e;
    }

    /** 把厂商池与设备规格池恢复到指定引擎（原序列号，deviceId 不变）。 */
    private void restorePool(SimulationEngine e) {
        for (VendorProfile vendor : vendors) {
            e.registerVendor(vendor);
        }
        for (DeviceSpec spec : deviceSpecs) {
            e.restoreDevice(spec.vendorName(), spec.serialNumber(), spec.deviceInfo());
        }
    }

    /**
     * Probes the connection-request listen port before the engine allocates
     * resources. Skipped in xmpp-only mode (no CwmpServer).
     *
     * @return null when the port is bindable, otherwise an actionable message
     */
    static String preflightPort(AppConfig config) {
        if (config.isXmppOnly()) {
            return null;
        }
        String bindHost = ConnectionRequestEndpoint.resolveBindHost(config);
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(bindHost, config.getConnReqPort()));
        } catch (Exception e) {
            return friendlyError(e, config);
        }
        // Windows 上关闭监听 socket 后端口可能被系统短暂保留，紧接着的正式
        // 绑定（Netty）会偶发 "Address already in use"；稍作等待消除自竞争
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Translates low-level startup failures into actionable messages.
     * Bind failures get port-specific guidance (e.g. Windows reserved port
     * ranges block binds without any visible listener).
     */
    static String friendlyError(Throwable t, AppConfig config) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindException
                    || String.valueOf(cause.getMessage()).contains("Address already in use")) {
                return "回连端口 " + config.getConnReqPort()
                        + " 无法绑定（被其他程序占用，或落在 Windows 系统保留端口段内）。"
                        + "请在「配置」页修改 conn-req-port（例如 17547），"
                        + "或用 netsh interface ipv4 show excludedportrange protocol=tcp 检查保留段";
            }
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private void notifyListeners(EngineState newState, String error) {
        for (StateListener listener : listeners) {
            try {
                listener.onStateChanged(newState, error);
            } catch (Throwable ignored) {
                // listener failures must not break the controller
            }
        }
    }
}
