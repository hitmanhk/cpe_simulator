package com.cpe.acsit.cwmp;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.acs.CwmpRpc;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CWMP fault-path coverage: invalid names (9005), type mismatches (9007),
 * read-only writes (9008), and unsupported methods (9000).
 */
class RpcFaultIT {

    @Test
    void getParameterValuesWithInvalidNameFaults9005() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.getParameterValuesRaw(
                    "Device.No.Such.Parameter");
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9005, response.faultCode());
        }
    }

    @Test
    void setParameterValuesOnReadOnlyFaults9008() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.setParameterValues(Map.of(
                    "Device.DeviceInfo.SerialNumber", "HACKED"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9008, response.faultCode());
        }
    }

    @Test
    void setParameterValuesWithWrongTypeFaults9007() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            // PeriodicInformInterval 是 unsignedInt，写入非数值串
            CompletableFuture<CwmpMessage> rpc = sim.acs.queueRpc(CwmpRpc.setParameterValuesTyped(
                    CwmpRpc.nextId(),
                    List.<String[]>of(new String[]{"Device.ManagementServer.PeriodicInformInterval",
                            "not-a-number", "xsd:unsignedInt"}),
                    "key"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9007, response.faultCode());
        }
    }

    @Test
    void setParameterValuesWithInvalidNameFaults9005() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.setParameterValues(Map.of(
                    "Device.Ghost.Parameter", "x"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9005, response.faultCode());
        }
    }

    @Test
    void unknownMethodFaults9000() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.queueRpc(
                    CwmpRpc.unknownMethod(CwmpRpc.nextId(), "Teleport"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9000, response.faultCode());
        }
    }

    @Test
    void deleteObjectWithInvalidPathFaults9005() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.queueRpc(
                    CwmpRpc.deleteObject(CwmpRpc.nextId(), "Device.WANDevice.99.", "key"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertTrue(response.isFault());
            assertEquals(9005, response.faultCode());
        }
    }
}
