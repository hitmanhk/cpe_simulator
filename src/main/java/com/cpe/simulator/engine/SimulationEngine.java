package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.cwmp.CwmpClient;
import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;
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
    private ScheduledExecutorService scheduler;
    private Instant startTime;

    public SimulationEngine(AppConfig config) {
        this.config = config;
        this.running = false;
    }

    public void start() throws Exception {
        SimLogger.info("Starting simulation engine");

        templateModel = ModelLoader.load(config.getModel());

        deviceManager = new DeviceManager(config, templateModel);
        cwmpClient = deviceManager.getCwmpClient();
        sessionSemaphore = deviceManager.getSessionSemaphore();

        List<CpeDevice> devices = deviceManager.createDevices(config.getCount());
        SimLogger.info("Created {} devices", devices.size());

        deviceRegistry = new DeviceRegistry();
        for (CpeDevice device : devices) {
            deviceRegistry.register(device.getDeviceId(), device);
        }

        if (!config.isXmppOnly()) {
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
            cwmpServer.start();
            SimLogger.info("Connection request server started on port {}", config.getConnReqPort());

            for (CpeDevice device : devices) {
                String connReqUrl = String.format("http://%s:%d/connReq/%s",
                    config.getConnReqHost(), config.getConnReqPort(), device.getDeviceId());
                try {
                    device.getDataModel().setRelativeValue("ManagementServer.ConnectionRequestURL", connReqUrl);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (config.isNatMode()) {
            xmppEngine = new XmppEngine(config, deviceId -> {
                CpeDevice device = (CpeDevice) deviceRegistry.getDevice(deviceId);
                if (device != null && !device.isSessionActive()) {
                    device.triggerInform("6 CONNECTION REQUEST");
                }
            });

            List<XmppConnection> xmppConnections = new ArrayList<>();
            for (CpeDevice device : devices) {
                XmppConnection conn = xmppEngine.createConnection(device.getDeviceId());
                conn.onStateChange(xmppState -> {
                    switch (xmppState) {
                        case ONLINE -> device.setState(CpeDevice.DeviceState.ONLINE);
                        case FAILED -> {
                            device.setState(CpeDevice.DeviceState.OFFLINE);
                            SimLogger.error("XMPP connection failed for device {}", device.getDeviceId());
                        }
                        case DISCONNECTED -> device.setState(CpeDevice.DeviceState.OFFLINE);
                        default -> {}
                    }
                });
                device.setXmppConnection(conn);
                xmppConnections.add(conn);
            }

            xmppEngine.start(xmppConnections);
            SimLogger.info("XMPP engine started with {} connections", xmppConnections.size());
        }

        informScheduler = new InformScheduler(config);
        scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        if (!config.isXmppOnly()) {
            batchConnector = new BatchConnector(config);
            batchConnector.connectBatch(devices).join();

            bootLauncher = new BootLauncher(config);
            bootLauncher.launchBoot(devices).join();
        }

        if (!config.isXmppOnly() && !config.isNoPeriodic()) {
            for (CpeDevice device : devices) {
                informScheduler.schedulePeriodicInform(device);
            }
        }

        running = true;
        startTime = Instant.now();
        SimLogger.info("Simulation engine started, {} devices active", devices.size());
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
