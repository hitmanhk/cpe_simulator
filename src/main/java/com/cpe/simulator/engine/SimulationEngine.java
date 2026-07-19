package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConnectionRequestEndpoint;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.cwmp.CwmpClient;
import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;
import com.cpe.simulator.xmpp.ConnReqAuthConfig;
import com.cpe.simulator.xmpp.XmppConnection;
import com.cpe.simulator.xmpp.XmppEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SimulationEngine {

    public record SimulationStatus(
        boolean running,
        String mode,
        int totalDevices,
        int onlineDevices,
        int activeSessions,
        long totalInforms,
        Duration uptime,
        long jvmMemoryUsed,
        long jvmMemoryMax,
        int threadCount
    ) {}

    private final AppConfig config;
    private DataModel templateModel;
    private DeviceManager deviceManager;
    private CwmpClient cwmpClient;
    private CwmpServer cwmpServer;
    private XmppEngine xmppEngine;
    private InformScheduler informScheduler;
    private BatchConnector batchConnector;
    private BootLauncher bootLauncher;
    private DeviceRegistry deviceRegistry;
    private Semaphore sessionSemaphore;
    private volatile boolean running;
    private volatile boolean initialized;
    private ScheduledExecutorService scheduler;
    private Instant startTime;

    public SimulationEngine(AppConfig config) {
        this.config = config;
        this.running = false;
    }

    /**
     * CLI start path: initializes, creates config.count devices and brings
     * them all online (connect + BOOTSTRAP + periodic scheduling).
     * Behavior is identical to the pre-split monolithic start().
     */
    public void start() throws Exception {
        initCore();
        List<CpeDevice> devices = deviceManager.createDevices(config.getCount());
        SimLogger.info("Created {} devices", devices.size());
        for (CpeDevice device : devices) {
            deviceRegistry.register(device.getDeviceId(), device);
        }
        startRuntime();

        if (!config.isXmppOnly()) {
            batchConnector = new BatchConnector(config);
            batchConnector.connectBatch(devices).join();

            bootLauncher = new BootLauncher(config);
            bootLauncher.launchBoot(devices).join();

            if (config.getInformSchedules().hasEnabledSchedule()) {
                for (CpeDevice device : devices) {
                    informScheduler.scheduleConfiguredInforms(device);
                }
            }
        }
        markRunning();
    }

    /**
     * GUI path phase 1: initializes the model and device manager without
     * opening any network listeners, so devices/vendors can be created before
     * the simulator is started. Idempotent.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            initCore();
        } catch (Exception e) {
            throw new RuntimeException("引擎初始化失败：" + e.getMessage(), e);
        }
        initialized = true;
    }

    /**
     * GUI path phase 2: starts the infrastructure only (connection request
     * listener / XMPP engine / schedulers). No devices are created; devices
     * are brought online individually via {@link #startDevices}.
     */
    public synchronized void startHeadless() {
        if (running) {
            return;
        }
        initialize();
        try {
            startRuntime();
        } catch (Exception e) {
            throw new RuntimeException("模拟器基础设施启动失败：" + e.getMessage(), e);
        }
        markRunning();
    }

    /**
     * Creates {@code count} devices under the vendor in CREATED state
     * (registered in the device registry, connection request URL written),
     * but does NOT bring them online — see {@link #startDevices}.
     */
    public synchronized List<CpeDevice> addDevices(String vendorName, int count,
                                                   java.util.Map<String, String> deviceInfoOverrides) {
        initialize();
        VendorProfile vendor = deviceManager.getVendor(vendorName);
        if (vendor == null) {
            throw new IllegalArgumentException("厂商不存在：" + vendorName);
        }
        List<CpeDevice> created = deviceManager.createDevices(vendor, count, deviceInfoOverrides);
        for (CpeDevice device : created) {
            deviceRegistry.register(device.getDeviceId(), device);
            if (!config.isXmppOnly()) {
                writeConnReqUrl(device);
            }
            if (config.isNatMode() && !config.isXmppOnly() && xmppEngine != null) {
                device.setXmppEngine(xmppEngine);
            }
        }
        SimLogger.info("Added {} device(s) under vendor '{}'", created.size(), vendorName);
        return created;
    }

    /**
     * Restores a single device with its original serial number (used when
     * the GUI rebuilds the pool after an engine restart so device identity
     * stays stable from the ACS point of view).
     */
    public synchronized CpeDevice restoreDevice(String vendorName, String serialNumber,
                                                java.util.Map<String, String> deviceInfoOverrides) {
        initialize();
        VendorProfile vendor = deviceManager.getVendor(vendorName);
        if (vendor == null) {
            throw new IllegalArgumentException("厂商不存在：" + vendorName);
        }
        CpeDevice device = deviceManager.createDeviceWithSerial(vendor, serialNumber, deviceInfoOverrides);
        deviceRegistry.register(device.getDeviceId(), device);
        if (!config.isXmppOnly()) {
            writeConnReqUrl(device);
        }
        if (config.isNatMode() && !config.isXmppOnly() && xmppEngine != null) {
            device.setXmppEngine(xmppEngine);
        }
        return device;
    }

    /**
     * Brings the given devices online: connect + 0 BOOTSTRAP + periodic
     * scheduling (public/normal NAT), or XMPP attach (xmpp-only mode).
     */
    public void startDevices(java.util.Collection<String> deviceIds) {
        if (!running) {
            throw new IllegalStateException("模拟器未启动，请先点击「启动模拟器」");
        }
        for (String deviceId : deviceIds) {
            CpeDevice device = (CpeDevice) deviceRegistry.getDevice(deviceId);
            if (device == null) {
                continue;
            }
            if (config.isXmppOnly()) {
                if (device.getXmppConnection() == null) {
                    XmppConnection conn = xmppEngine.createConnection(
                            device.getDeviceId(), device.getSerialNumber());
                    XmppOnlyInformBridge stateBridge = new XmppOnlyInformBridge(device, informScheduler);
                    conn.onStateChange(stateBridge::onStateChange);
                    device.setXmppConnection(conn);
                    xmppEngine.addConnection(conn);
                }
            } else {
                device.connect();
                device.triggerInform("0 BOOTSTRAP");
                if (config.getInformSchedules().hasEnabledSchedule()) {
                    informScheduler.scheduleConfiguredInforms(device);
                }
            }
        }
    }

    /** Takes the given devices offline: cancels periodic scheduling and disconnects. */
    public void stopDevices(java.util.Collection<String> deviceIds) {
        for (String deviceId : deviceIds) {
            CpeDevice device = deviceRegistry != null
                    ? (CpeDevice) deviceRegistry.getDevice(deviceId) : null;
            if (device == null) {
                continue;
            }
            if (informScheduler != null) {
                informScheduler.cancelScheduledInform(deviceId);
            }
            device.disconnect();
        }
    }

    /** Registers (or replaces) a vendor profile. Initializes lazily. */
    public synchronized void registerVendor(VendorProfile vendor) {
        initialize();
        deviceManager.registerVendor(vendor);
    }

    public List<VendorProfile> getVendors() {
        return deviceManager != null
                ? deviceManager.getVendors()
                : List.of(VendorProfile.defaultVendor(config));
    }

    // ---------------- 启动流程内部阶段 ----------------

    private void initCore() throws Exception {
        SimLogger.configure(config.getLogging().getLevel(), config.getLogging().getDeviceFilter());
        SimLogger.info("Initializing simulation engine core");
        config.validateInformSchedules();

        templateModel = ModelLoader.load(config.getModel());

        deviceManager = new DeviceManager(config, templateModel);
        cwmpClient = deviceManager.getCwmpClient();
        sessionSemaphore = deviceManager.getSessionSemaphore();

        deviceRegistry = new DeviceRegistry();
        informScheduler = new InformScheduler(config);
        scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    }

    private void startRuntime() throws Exception {
        if (!config.isXmppOnly() && cwmpServer == null) {
            cwmpServer = new CwmpServer(config, deviceId -> {
                CpeDevice device = (CpeDevice) deviceRegistry.getDevice(deviceId);
                if (device == null) {
                    return new CwmpServer.ConnectionRequestResult(false, 404, "Device not found");
                }
                if (device.getState() == CpeDevice.DeviceState.OFFLINE) {
                    return new CwmpServer.ConnectionRequestResult(false, 503, "Device offline");
                }
                if (device.isSessionActive()) {
                    return new CwmpServer.ConnectionRequestResult(true, 200, "OK");
                }
                device.triggerInform("6 CONNECTION REQUEST");
                return new CwmpServer.ConnectionRequestResult(true, 200, "OK");
            });
            cwmpServer.setCredentialProvider(this::resolveConnReqCredentials);
            cwmpServer.start();
            SimLogger.info("Connection request server started on {}:{}",
                ConnectionRequestEndpoint.resolveBindHost(config), config.getConnReqPort());

            for (CpeDevice device : deviceManager.getDevices()) {
                writeConnReqUrl(device);
            }
        }

        if (config.isNatMode() && xmppEngine == null) {
            ConnReqAuthConfig connReqAuthConfig = new ConnReqAuthConfig(
                config.isConnReqAuthEnabled(),
                config.getConnReqUser(),
                config.getConnReqPassword(),
                this::resolveConnReqCredentials);

            xmppEngine = new XmppEngine(config, deviceId -> {
                CpeDevice device = (CpeDevice) deviceRegistry.getDevice(deviceId);
                if (device == null) {
                    SimLogger.warn("[{}] XMPP ConnectionRequest ignored: device not found", deviceId);
                } else if (device.isSessionActive()) {
                    SimLogger.xmppEvent(deviceId, "CONNECTION_REQUEST_CWMP_SKIPPED",
                        "reason=cwmp_session_active");
                } else {
                    SimLogger.xmppEvent(deviceId, "CONNECTION_REQUEST_CWMP_TRIGGERED",
                        "eventCode=6 CONNECTION REQUEST");
                    device.triggerInform("6 CONNECTION REQUEST");
                }
            }, connReqAuthConfig);

            if (config.isXmppOnly()) {
                List<XmppConnection> xmppConnections = new ArrayList<>();
                for (CpeDevice device : deviceManager.getDevices()) {
                    XmppConnection conn = xmppEngine.createConnection(
                        device.getDeviceId(), device.getSerialNumber());
                    XmppOnlyInformBridge stateBridge = new XmppOnlyInformBridge(device, informScheduler);
                    conn.onStateChange(stateBridge::onStateChange);
                    device.setXmppConnection(conn);
                    xmppConnections.add(conn);
                }
                xmppEngine.start(xmppConnections);
                SimLogger.info("XMPP engine started with {} connections (xmpp-only mode)", xmppConnections.size());
            } else {
                for (CpeDevice device : deviceManager.getDevices()) {
                    device.setXmppEngine(xmppEngine);
                }
                xmppEngine.start(List.of());
                SimLogger.info("XMPP engine started, waiting for ACS to provision XMPP credentials");
            }
        }
    }

    private void writeConnReqUrl(CpeDevice device) {
        String connReqUrl = ConnectionRequestEndpoint.buildUrl(config, device.getDeviceId());
        try {
            device.getDataModel().setRelativeValue("ManagementServer.ConnectionRequestURL", connReqUrl);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Per-device connection-request credentials: vendor credentials from the
     * device's vendor profile win; null means "fall back to global config"
     * (which itself may mean auth disabled), preserving CLI behavior.
     */
    private String[] resolveConnReqCredentials(String deviceId) {
        if (deviceRegistry == null || deviceManager == null) {
            return null;
        }
        CpeDevice device = (CpeDevice) deviceRegistry.getDevice(deviceId);
        if (device == null || device.getVendorName() == null) {
            return null;
        }
        VendorProfile vendor = deviceManager.getVendor(device.getVendorName());
        return vendor != null && vendor.hasConnReqAuth()
                ? new String[]{vendor.connReqUser(), vendor.connReqPassword()}
                : null;
    }

    private void markRunning() {
        running = true;
        startTime = Instant.now();
        SimLogger.info("Simulation engine started, {} devices registered",
                deviceManager != null ? deviceManager.getDeviceCount() : 0);
    }

    public void stop() {
        SimLogger.info("Stopping simulation engine");
        running = false;

        if (informScheduler != null) {
            informScheduler.shutdown();
        }

        if (sessionSemaphore != null) {
            try {
                boolean drained = sessionSemaphore.tryAcquire(
                    config.getMaxSessions(), 30, TimeUnit.SECONDS);
                if (drained) {
                    sessionSemaphore.release(config.getMaxSessions());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (xmppEngine != null) {
            xmppEngine.stop();
        }

        if (cwmpServer != null) {
            cwmpServer.stop();
        }

        if (deviceManager != null) {
            deviceManager.shutdown();
        }

        if (batchConnector != null) {
            batchConnector.shutdown();
        }

        if (bootLauncher != null) {
            bootLauncher.shutdown();
        }

        if (scheduler != null) {
            scheduler.shutdown();
        }

        SimLogger.info("Simulation engine stopped");
    }

    public void stopForced() {
        SimLogger.info("Force stopping simulation engine");
        running = false;

        if (informScheduler != null) {
            informScheduler.shutdown();
        }
        if (xmppEngine != null) {
            xmppEngine.stop();
        }
        if (cwmpServer != null) {
            cwmpServer.stop();
        }
        if (deviceManager != null) {
            deviceManager.shutdown();
        }
        if (batchConnector != null) {
            batchConnector.shutdown();
        }
        if (bootLauncher != null) {
            bootLauncher.shutdown();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        SimLogger.info("Simulation engine force stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public SimulationStatus getStatus() {
        int totalDevices = deviceManager != null ? deviceManager.getDeviceCount() : 0;
        int onlineDevices = 0;
        long totalInforms = 0;

        if (deviceRegistry != null) {
            for (var ctx : deviceRegistry.getAllDevices()) {
                CpeDevice device = (CpeDevice) ctx;
                if (device.getState() == CpeDevice.DeviceState.ONLINE) {
                    onlineDevices++;
                }
                totalInforms += device.getInformCount();
            }
        }

        int activeSessions = sessionSemaphore != null
            ? config.getMaxSessions() - sessionSemaphore.availablePermits()
            : 0;

        Duration uptime = startTime != null ? Duration.between(startTime, Instant.now()) : Duration.ZERO;

        return new SimulationStatus(
            running,
            config.getMode(),
            totalDevices,
            onlineDevices,
            activeSessions,
            totalInforms,
            uptime,
            ResourceMonitor.getJvmMemoryUsed(),
            ResourceMonitor.getJvmMemoryMax(),
            ResourceMonitor.getThreadCount()
        );
    }

    public DeviceRegistry getDeviceRegistry() {
        return deviceRegistry;
    }

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }
}
