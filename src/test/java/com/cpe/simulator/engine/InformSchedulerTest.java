package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InformSchedulerTest {

    @Test
    void schedulesOnlyEnabledEventTypes() {
        AppConfig config = new AppConfig();
        config.getInformSchedules().getBootstrap().setInterval(86400);
        config.getInformSchedules().getBoot().setInterval(0);
        config.getInformSchedules().getPeriodic().setInterval(300);

        CpeDevice device = mock(CpeDevice.class);
        when(device.getDeviceId()).thenReturn("device-1");

        InformScheduler scheduler = new InformScheduler(config);
        try {
            scheduler.scheduleConfiguredInforms(device);
            assertEquals(2, scheduler.getScheduledTaskCount());

            scheduler.cancelScheduledInform("device-1");
            assertEquals(0, scheduler.getScheduledTaskCount());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void initialJitterStaysWithinConfiguredRange() {
        AppConfig config = new AppConfig();
        InformScheduler scheduler = new InformScheduler(config);
        try {
            for (int i = 0; i < 100; i++) {
                long delay = scheduler.computeJitteredInterval(300, 0.2);
                assertTrue(delay >= 240 && delay <= 360);
            }
            assertEquals(300, scheduler.computeJitteredInterval(300, 0.0));
        } finally {
            scheduler.shutdown();
        }
    }
}
