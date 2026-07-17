package com.cpe.simulator.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InformScheduleConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsIndependentInformSchedules() throws Exception {
        Path configFile = tempDir.resolve("simulator.yaml");
        Files.writeString(configFile, """
            simulator:
              inform-schedules:
                bootstrap:
                  interval: 86400
                  initial-jitter: 0.1
                boot:
                  interval: 0
                  initial-jitter: 0.2
                periodic:
                  interval: 120
                  initial-jitter: 0.8
            """);

        AppConfig config = ConfigLoader.load(configFile.toString());

        assertEquals(86400, config.getInformSchedules().getBootstrap().getInterval());
        assertEquals(0, config.getInformSchedules().getBoot().getInterval());
        assertEquals(120, config.getInformSchedules().getPeriodic().getInterval());
        assertEquals(0.8, config.getInformSchedules().getPeriodic().getInitialJitter(), 0.001);
    }

    @Test
    void keepsPerEventDefaultsForOmittedValues() throws Exception {
        Path configFile = tempDir.resolve("minimal.yaml");
        Files.writeString(configFile, """
            simulator:
              inform-schedules:
                bootstrap:
                  interval: 7200
            """);

        AppConfig config = ConfigLoader.load(configFile.toString());

        assertEquals(7200, config.getInformSchedules().getBootstrap().getInterval());
        assertEquals(0.2, config.getInformSchedules().getBootstrap().getInitialJitter(), 0.001);
        assertEquals(300, config.getInformSchedules().getPeriodic().getInterval());
    }

    @Test
    void rejectsNegativeScheduleInterval() {
        AppConfig config = new AppConfig();
        config.getInformSchedules().getBootstrap().setInterval(-1);

        assertThrows(IllegalArgumentException.class, config::validateInformSchedules);
    }
}
