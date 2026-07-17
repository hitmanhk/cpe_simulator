package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @Test
    void repeatedOnlineNotificationDoesNotDuplicateSchedules() {
        AppConfig config = new AppConfig();
        config.getInformSchedules().getBootstrap().setInterval(0);
        config.getInformSchedules().getBoot().setInterval(0);
        config.getInformSchedules().getPeriodic().setInterval(90);

        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor)
            .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS));

        CpeDevice device = mock(CpeDevice.class);
        when(device.getDeviceId()).thenReturn("device-1");
        InformScheduler scheduler = new InformScheduler(config, executor);

        scheduler.scheduleConfiguredInforms(device);
        scheduler.scheduleConfiguredInforms(device);

        assertEquals(1, scheduler.getScheduledTaskCount());
        verify(executor, times(1))
            .scheduleAtFixedRate(any(Runnable.class), anyLong(), eq(90L), eq(TimeUnit.SECONDS));

        scheduler.cancelScheduledInform("device-1");
        scheduler.scheduleConfiguredInforms(device);
        verify(executor, times(2))
            .scheduleAtFixedRate(any(Runnable.class), anyLong(), eq(90L), eq(TimeUnit.SECONDS));
    }
}
