package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class CpeDeviceAuthTest {

    @Test
    void deviceStateIsOnlineAfterConnect() {
        AppConfig config = new AppConfig();
        CwmpClient client = new CwmpClient(config);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Semaphore semaphore = new Semaphore(10);

        CpeDevice device = new CpeDevice(
                "test-001", "SN001", "000000", "SimCPE",
                null, config, client, scheduler, semaphore);
        device.connect();

        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        scheduler.shutdown();
        client.shutdown();
    }
}
