package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.IdGenerator;
import com.cpe.simulator.cwmp.CwmpClient;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelTemplate;

import java.util.ArrayList;
import java.util.List;
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

    public DeviceManager(AppConfig config, DataModel templateModel) {
        this.config = config;
        this.templateModel = templateModel;
        this.cwmpClient = new CwmpClient(config);
        this.devices = new CopyOnWriteArrayList<>();
        this.nextIndex = new AtomicInteger(0);
        this.sessionSemaphore = new Semaphore(config.getMaxSessions());
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Thread.ofVirtual().factory()
        );
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

        DataModel deviceModel = templateModel.deepCopy();

        String connReqUrl = String.format("http://%s:%d/connReq/%s",
            config.getConnReqHost(), config.getConnReqPort(), deviceId);

        ModelTemplate.TemplateContext ctx = new ModelTemplate.TemplateContext(
            serialNumber, config.getOui(), config.getProductClass(),
            config.getAcsUrl(), connReqUrl
        );
        ModelTemplate.resolveModel(deviceModel, ctx);

        CpeDevice device = new CpeDevice(
            deviceId, serialNumber, config.getOui(), config.getProductClass(),
            deviceModel, config, cwmpClient, scheduler, sessionSemaphore
        );
        device.initialize();
        return device;
    }

    public void addDevices(int count) {
        List<CpeDevice> newDevices = createDevices(count);
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
