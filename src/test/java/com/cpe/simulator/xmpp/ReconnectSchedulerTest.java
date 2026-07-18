package com.cpe.simulator.xmpp;

import com.cpe.simulator.common.AppConfig;
import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconnectSchedulerTest {

    private ScheduledExecutorService mockScheduler;
    private EventLoopGroup mockEventLoopGroup;
    private List<XmppConnection> connections;
    private AtomicInteger scheduleCount;
    private List<Long> scheduledDelays;

    @BeforeEach
    void setUp() {
        mockScheduler = mock(ScheduledExecutorService.class);
        mockEventLoopGroup = mock(EventLoopGroup.class);
        connections = new ArrayList<>();
        scheduleCount = new AtomicInteger(0);
        scheduledDelays = new ArrayList<>();

        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                scheduleCount.incrementAndGet();
                long delay = invocation.getArgument(1);
                scheduledDelays.add(delay);
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });
    }

    private ReconnectScheduler createScheduler() {
        return new ReconnectScheduler(mockScheduler,
            deviceId -> connections.stream()
                .filter(c -> c.getDeviceId().equals(deviceId))
                .findFirst().orElse(null),
            mockEventLoopGroup);
    }

    @Test
    void testDedupIgnoresSecondFailureForSameDevice() {
        AtomicInteger localScheduleCount = new AtomicInteger(0);
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                localScheduleCount.incrementAndGet();
                return null;
            });

        ReconnectScheduler scheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        scheduler.onFailure("device001");
        scheduler.onFailure("device001");

        assertEquals(1, localScheduleCount.get());
    }

    @Test
    void testDifferentDevicesAreNotDeduped() {
        AtomicInteger localScheduleCount = new AtomicInteger(0);
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                localScheduleCount.incrementAndGet();
                return null;
            });

        ReconnectScheduler scheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        scheduler.onFailure("device001");
        scheduler.onFailure("device002");

        assertEquals(2, localScheduleCount.get());
    }

    @Test
    void testOnSuccessResetsBackoff() {
        ReconnectScheduler scheduler = createScheduler();
        scheduledDelays.clear();

        scheduler.onFailure("device001");
        long firstDelay = scheduledDelays.get(0);

        scheduler.onSuccess("device001");

        scheduledDelays.clear();
        scheduler.onFailure("device001");
        long secondDelay = scheduledDelays.get(0);

        assertTrue(secondDelay < 45_000,
            "After reset, delay should be ~30s not escalated. Got: " + secondDelay + "ms");
    }

    @Test
    void testIsPendingReturnsTrueForScheduledDevice() {
        AtomicInteger localScheduleCount = new AtomicInteger(0);
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                localScheduleCount.incrementAndGet();
                return null;
            });

        ReconnectScheduler pendingScheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        pendingScheduler.onFailure("device001");

        assertTrue(pendingScheduler.isPending("device001"));
        assertFalse(pendingScheduler.isPending("device002"));
    }

    @Test
    void testShutdownClearsPendingSet() {
        ScheduledExecutorService nonExecutingScheduler = mock(ScheduledExecutorService.class);
        when(nonExecutingScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> null);

        ReconnectScheduler scheduler = new ReconnectScheduler(nonExecutingScheduler,
            deviceId -> null, mockEventLoopGroup);

        scheduler.onFailure("device001");
        assertTrue(scheduler.isPending("device001"));

        scheduler.shutdown();
        assertFalse(scheduler.isPending("device001"));
    }

    @Test
    void testReconnectCallsConnectOnFoundConnection() {
        ScheduledExecutorService immediateScheduler = mock(ScheduledExecutorService.class);
        when(immediateScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });

        XmppConnection mockConn = mock(XmppConnection.class);
        when(mockConn.getDeviceId()).thenReturn("device001");
        connections.add(mockConn);

        ReconnectScheduler scheduler = new ReconnectScheduler(immediateScheduler,
            deviceId -> connections.stream()
                .filter(c -> c.getDeviceId().equals(deviceId))
                .findFirst().orElse(null),
            mockEventLoopGroup);

        scheduler.onFailure("device001");

        verify(mockConn, times(1)).connect(mockEventLoopGroup);
    }

    @Test
    void testReconnectSkipsIfConnectionNotFound() {
        ScheduledExecutorService immediateScheduler = mock(ScheduledExecutorService.class);
        when(immediateScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });

        ReconnectScheduler scheduler = new ReconnectScheduler(immediateScheduler,
            deviceId -> null,
            mockEventLoopGroup);

        assertDoesNotThrow(() -> scheduler.onFailure("nonexistent"));
    }

    @Test
    void testUsesConfiguredInitialDelay() {
        AppConfig.XmppConfig config = new AppConfig.XmppConfig();
        config.setReconnectInitialDelay(120);
        config.setReconnectMaxDelay(600);
        ReconnectScheduler scheduler = new ReconnectScheduler(mockScheduler,
            deviceId -> connections.stream()
                .filter(c -> c.getDeviceId().equals(deviceId))
                .findFirst().orElse(null),
            mockEventLoopGroup,
            config);
        scheduledDelays.clear();

        scheduler.onFailure("device001");

        long delay = scheduledDelays.get(0);
        assertTrue(delay >= 108_000 && delay <= 132_000,
            "Configured initial delay should be ~120s, got: " + delay + "ms");
    }
}
