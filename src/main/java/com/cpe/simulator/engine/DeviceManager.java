package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConnectionRequestEndpoint;
import com.cpe.simulator.common.IdGenerator;
import com.cpe.simulator.cwmp.CwmpClient;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceManager {

    private final AppConfig config;
    private final DataModel templateModel;
    private final CwmpClient cwmpClient;
    private final List<CpeDevice> devices;
    private final AtomicInteger nextIndex;
    private final Semaphore sessionSemaphore;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, VendorProfile> vendors;

    public DeviceManager(AppConfig config, DataModel templateModel) {
        this.config = config;
        this.templateModel = templateModel;
        this.cwmpClient = new CwmpClient(config);
        this.devices = new CopyOnWriteArrayList<>();
        this.nextIndex = new AtomicInteger(config.getSerialStart());
        this.sessionSemaphore = new Semaphore(config.getMaxSessions());
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Thread.ofVirtual().factory()
        );
        this.vendors = new ConcurrentHashMap<>();
        this.vendors.put(VendorProfile.DEFAULT_VENDOR_NAME, VendorProfile.defaultVendor(config));
    }

    public List<CpeDevice> createDevices(int count) {
        List<CpeDevice> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CpeDevice device = createDevice(nextIndex.getAndIncrement());
            created.add(device);
            devices.add(device);
        }
        return created;
    }

    public CpeDevice createDevice(int index) {
        String serialNumber = IdGenerator.serialNumber(config.getSerialPrefix(), index);
        String deviceId = IdGenerator.deviceId(config.getOui(), config.getProductClass(), serialNumber);

        DataModel deviceModel = templateModel.sharedCopy();

        String connReqUrl = ConnectionRequestEndpoint.buildUrl(config, deviceId);

        ModelTemplate.TemplateContext ctx = new ModelTemplate.TemplateContext(
            serialNumber, config.getOui(), config.getProductClass(),
            config.getAcsUrl(), connReqUrl
        );
        ModelTemplate.resolveModel(deviceModel, ctx);
        deviceModel.saveDefaults();

        CpeDevice device = new CpeDevice(
            deviceId, serialNumber, config.getOui(), config.getProductClass(),
            deviceModel, config, cwmpClient, scheduler, sessionSemaphore
        );
        device.initialize();
        return device;
    }

    // ---------------- 厂商管理 ----------------

    /** 注册（或替换）厂商。默认厂商始终存在。 */
    public void registerVendor(VendorProfile vendor) {
        vendors.put(vendor.name(), vendor);
    }

    public VendorProfile getVendor(String name) {
        return vendors.get(name);
    }

    public List<VendorProfile> getVendors() {
        return List.copyOf(vendors.values());
    }

    /** 删除厂商；默认厂商不可删除。 */
    public boolean removeVendor(String name) {
        if (VendorProfile.DEFAULT_VENDOR_NAME.equals(name)) {
            return false;
        }
        return vendors.remove(name) != null;
    }

    /**
     * Creates {@code count} devices under the given vendor in CREATED state.
     * Vendor fields (Manufacturer / OUI / ProductClass / connection-request
     * credentials) are written into each device's data model from the vendor
     * profile; {@code deviceInfoOverrides} carries the remaining per-device
     * DeviceInfo values (e.g. SoftwareVersion / HardwareVersion) using
     * root-relative paths like {@code DeviceInfo.SoftwareVersion}.
     */
    public List<CpeDevice> createDevices(VendorProfile vendor, int count, Map<String, String> deviceInfoOverrides) {
        List<CpeDevice> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String serialNumber = IdGenerator.serialNumber(
                    config.getSerialPrefix(), nextIndex.getAndIncrement());
            created.add(createDeviceWithSerial(vendor, serialNumber, deviceInfoOverrides));
        }
        return created;
    }

    /** Creates a single device with an explicit serial number (used when restoring the pool). */
    public CpeDevice createDeviceWithSerial(VendorProfile vendor, String serialNumber,
                                            Map<String, String> deviceInfoOverrides) {
        String deviceId = IdGenerator.deviceId(vendor.oui(), vendor.productClass(), serialNumber);

        DataModel deviceModel = templateModel.sharedCopy();
        String connReqUrl = ConnectionRequestEndpoint.buildUrl(config, deviceId);
        ModelTemplate.TemplateContext ctx = new ModelTemplate.TemplateContext(
                serialNumber, vendor.oui(), vendor.productClass(),
                config.getAcsUrl(), connReqUrl);
        ModelTemplate.resolveModel(deviceModel, ctx);

        // 厂商字段写入模型树（ACS 侧可见，厂商配置为单一来源）
        setRelativeIfPresent(deviceModel, "DeviceInfo.Manufacturer", vendor.manufacturer());
        if (vendor.hasConnReqAuth()) {
            setRelativeIfPresent(deviceModel, "ManagementServer.ConnectionRequestUsername", vendor.connReqUser());
            setRelativeIfPresent(deviceModel, "ManagementServer.ConnectionRequestPassword", vendor.connReqPassword());
        }
        // 设备级 DeviceInfo 覆盖（厂商字段不在此列）
        if (deviceInfoOverrides != null) {
            deviceInfoOverrides.forEach((path, value) -> setRelativeIfPresent(deviceModel, path, value));
        }
        deviceModel.saveDefaults();

        CpeDevice device = new CpeDevice(
                deviceId, serialNumber, vendor.oui(), vendor.productClass(),
                deviceModel, config, cwmpClient, scheduler, sessionSemaphore);
        device.setVendorName(vendor.name());
        device.initialize();
        devices.add(device);
        return device;
    }

    /** 以相对路径写值；参数不存在时忽略（tr098 缺少部分 TR-181 参数）。 */
    private static void setRelativeIfPresent(DataModel model, String relativePath, String value) {
        if (value == null) {
            return;
        }
        try {
            model.setRelativeValue(relativePath, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Dynamically creates and registers additional devices.
     *
     * @return the newly created devices (callers may ignore it)
     */
    public List<CpeDevice> addDevices(int count) {
        return createDevices(count);
    }

    public void removeDevice(String deviceId) {
        CpeDevice device = getDevice(deviceId);
        if (device != null) {
            device.disconnect();
            devices.remove(device);
        }
    }

    public List<CpeDevice> getDevices() {
        return new ArrayList<>(devices);
    }

    public CpeDevice getDevice(String deviceId) {
        for (CpeDevice device : devices) {
            if (device.getDeviceId().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }

    public int getDeviceCount() {
        return devices.size();
    }

    public Semaphore getSessionSemaphore() {
        return sessionSemaphore;
    }

    public CwmpClient getCwmpClient() {
        return cwmpClient;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void shutdown() {
        scheduler.shutdown();
        cwmpClient.shutdown();
    }
}
