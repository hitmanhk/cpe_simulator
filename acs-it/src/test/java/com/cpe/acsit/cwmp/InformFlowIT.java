package com.cpe.acsit.cwmp;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: engine bootstrap Inform reaches the ACS with the expected
 * TR-069 structure, and a scripted GetParameterValues round-trip works
 * inside the same CWMP session.
 */
class InformFlowIT {

    @Test
    void bootstrapInformCarriesDeviceIdentityAndParameters() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            // RPC 必须先入队：BOOTSTRAP Inform 之后 CPE 立即发 empty POST，
            // ACS 在同一会话内下发排队的 RPC
            CompletableFuture<Map<String, String>> rpcFuture = sim.acs.getParameterValues(
                    "Device.DeviceInfo.SerialNumber", "Device.DeviceInfo.SoftwareVersion");
            sim.start();

            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertEquals("SimCPE", inform.productClass());
            assertEquals("000000", inform.oui());
            assertTrue(inform.serialNumber().startsWith("SIM"),
                    "serial should carry the configured prefix: " + inform.serialNumber());

            Map<String, String> params = inform.parameterValues();
            assertEquals("SimCPE", params.get("Device.DeviceInfo.ProductClass"));
            assertNotNull(params.get("Device.DeviceInfo.Manufacturer"));
            assertEquals(inform.serialNumber(), params.get("Device.DeviceInfo.SerialNumber"));
            String connReqUrl = params.get("Device.ManagementServer.ConnectionRequestURL");
            assertNotNull(connReqUrl, "ConnectionRequestURL must be advertised");
            assertTrue(connReqUrl.contains("/connReq/"), "unexpected ConnectionRequestURL: " + connReqUrl);
            assertTrue(connReqUrl.contains("127.0.0.1"), "should advertise loopback: " + connReqUrl);

            Map<String, String> values = rpcFuture.get(30, TimeUnit.SECONDS);
            assertEquals(inform.serialNumber(), values.get("Device.DeviceInfo.SerialNumber"));
            assertNotNull(values.get("Device.DeviceInfo.SoftwareVersion"));
        }
    }
}
