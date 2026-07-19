package com.cpe.acsit.auth;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACS-side HTTP authentication coverage: Basic, Digest MD5, Digest SHA-256,
 * and cookie session tracking. A completed Inform proves the simulator's
 * credentials were accepted by the (verifying) test server.
 */
class AcsAuthIT {

    @Test
    void basicAuthAcceptedWithCorrectCredentials() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.useBasicAuth("admin", "admin"); // harness 默认 acs-user/pass = admin/admin
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertNotNull(inform.serialNumber());
        }
    }

    @Test
    void basicAuthRejectsWrongPassword() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.useBasicAuth("admin", "expected-password");
            sim.configure(c -> c.setAcsPassword("wrong-password"));
            sim.start();

            // 401 后 inform 会话失败，Inform 不应到达
            Thread.sleep(5000);
            assertEquals(0, sim.acs.countInforms(),
                    "wrong credentials must not complete an Inform");
        }
    }

    @Test
    void digestMd5Accepted() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.useDigestAuth("admin", "admin", "MD5");
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertNotNull(inform.serialNumber());
        }
    }

    @Test
    void digestSha256Accepted() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.useDigestAuth("admin", "admin", "SHA-256");
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertNotNull(inform.serialNumber());
        }
    }

    @Test
    void digestRejectsWrongPassword() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.useDigestAuth("admin", "expected-password", "MD5");
            sim.configure(c -> c.setAcsPassword("wrong-password"));
            sim.start();

            Thread.sleep(5000);
            assertEquals(0, sim.acs.countInforms(),
                    "wrong digest credentials must not complete an Inform");
        }
    }

    @Test
    void cookiesSetByAcsAreReturnedWithinSession() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.acs.enableCookies(true);
            // 排一个 RPC 迫使会话继续（empty POST + RPC 响应都应带 Cookie）
            sim.acs.getParameterValues("Device.DeviceInfo.SerialNumber");
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            SimHarness.awaitTrue(() -> !sim.acs.getObservedCookies().isEmpty(),
                    Duration.ofSeconds(15), "CPE should echo the session cookie");

            assertTrue(sim.acs.getObservedCookies().stream()
                            .anyMatch(c -> c.contains("ACSSESSION=")),
                    "cookie header should carry ACSSESSION: " + sim.acs.getObservedCookies());
        }
    }
}
