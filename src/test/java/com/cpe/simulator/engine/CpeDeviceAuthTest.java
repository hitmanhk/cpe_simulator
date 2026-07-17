package com.cpe.simulator.engine;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class CpeDeviceAuthTest {

    @Test
    void connectRecordsDeviceStateTransitions() {
        Logger logger = (Logger) LoggerFactory.getLogger("SimLogger");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        AppConfig config = new AppConfig();
        CwmpClient client = new CwmpClient(config);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Semaphore semaphore = new Semaphore(10);

        try {
            CpeDevice device = new CpeDevice(
                    "test-001", "SN001", "000000", "SimCPE",
                    null, config, client, scheduler, semaphore);
            device.connect();

            assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage).anyMatch(message ->
                message.contains("[test-001] Connection state changed: type=DEVICE")
                    && message.contains("previous=CREATED current=CONNECTING")
                    && message.contains("reason=connection requested")));
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage).anyMatch(message ->
                message.contains("[test-001] Connection state changed: type=DEVICE")
                    && message.contains("previous=CONNECTING current=ONLINE")
                    && message.contains("reason=public mode ready")));

            int transitionCount = appender.list.size();
            device.setState(CpeDevice.DeviceState.ONLINE);
            assertEquals(transitionCount, appender.list.size(), "Duplicate states must not be logged");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            scheduler.shutdown();
            client.shutdown();
        }
    }
}
