package com.cpe.simulator.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UdsClientTest {

    @Test
    void getDefaultSocketPathReturnsNonNull() {
        String path = UdsClient.getDefaultSocketPath();
        assertNotNull(path);
        assertFalse(path.isEmpty());
        assertTrue(path.endsWith("cpe-sim.sock"));
    }

    @Test
    void getDefaultSocketPathContainsValidDirectory() {
        String path = UdsClient.getDefaultSocketPath();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            assertTrue(path.contains("\\"));
        } else {
            assertTrue(path.startsWith("/tmp/") || path.startsWith("/var/"));
        }
    }
}
