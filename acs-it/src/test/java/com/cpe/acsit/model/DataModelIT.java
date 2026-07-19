package com.cpe.acsit.model;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-model coverage: TR-181 vs TR-098 trees, template-variable resolution,
 * and notification=2 driven 4 VALUE CHANGE informs.
 */
class DataModelIT {

    @Test
    void tr181ModelResolvesTemplateVariables() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<Map<String, String>> rpc = sim.acs.getParameterValues(
                    "Device.DeviceInfo.SerialNumber",
                    "Device.DeviceInfo.Manufacturer",
                    "Device.DeviceInfo.ProductClass",
                    "Device.ManagementServer.URL",
                    "Device.ManagementServer.ConnectionRequestURL",
                    "Device.WiFi.SSID.1.SSID");
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            Map<String, String> values = rpc.get(30, TimeUnit.SECONDS);
            assertEquals(inform.serialNumber(), values.get("Device.DeviceInfo.SerialNumber"));
            assertEquals(inform.manufacturer(), values.get("Device.DeviceInfo.Manufacturer"));
            assertEquals("SimCPE", values.get("Device.DeviceInfo.ProductClass"));
            assertEquals(sim.config.getAcsUrl(), values.get("Device.ManagementServer.URL"));
            String connReqUrl = values.get("Device.ManagementServer.ConnectionRequestURL");
            assertTrue(connReqUrl != null && connReqUrl.contains("/connReq/"),
                    "conn.req.url template var should resolve: " + connReqUrl);
            assertNotNull(values.get("Device.WiFi.SSID.1.SSID"),
                    "TR-181 WiFi SSID path must exist");
        }
    }

    @Test
    void tr098ModelUsesInternetGatewayDeviceRoot() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.configure(c -> c.setModel(SimHarness.MODEL_TR098));
            CompletableFuture<Map<String, String>> rpc = sim.acs.getParameterValues(
                    "InternetGatewayDevice.DeviceInfo.SerialNumber",
                    "InternetGatewayDevice.ManagementServer.URL",
                    "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID");
            CompletableFuture<CwmpMessage> tr181RootShouldFail = sim.acs.getParameterValuesRaw(
                    "Device.DeviceInfo.SerialNumber");
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            Map<String, String> values = rpc.get(30, TimeUnit.SECONDS);
            assertEquals(inform.serialNumber(),
                    values.get("InternetGatewayDevice.DeviceInfo.SerialNumber"));
            assertEquals(sim.config.getAcsUrl(),
                    values.get("InternetGatewayDevice.ManagementServer.URL"));
            assertNotNull(values.get("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"),
                    "TR-098 WLAN path must exist");

            CwmpMessage fault = tr181RootShouldFail.get(30, TimeUnit.SECONDS);
            assertTrue(fault.isFault());
            assertEquals(9005, fault.faultCode(), "TR-098 model must not expose TR-181 root");
        }
    }

    @Test
    void notificationTwoParameterChangeTriggersValueChangeInform() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            // XMPP Username：writable 且 notification=2（设值不会真的连 XMPP——无密码）
            CompletableFuture<CwmpMessage> set = sim.acs.setParameterValues(Map.of(
                    "Device.XMPP.Connection.1.Username", "changed-user-42"));
            sim.start();
            CwmpMessage bootstrap = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage setResponse = set.get(30, TimeUnit.SECONDS);
            assertFalse(setResponse.isFault());
            assertEquals(0, setResponse.intField("Status", -1));

            CwmpMessage valueChange = sim.acs.awaitInformWithEvent("4 VALUE CHANGE",
                    Duration.ofSeconds(30));
            assertEquals(bootstrap.serialNumber(), valueChange.serialNumber(),
                    "VALUE CHANGE inform must come from the same device");
        }
    }

    @Test
    void randomIpTemplateProduces10xAddresses() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.configure(c -> c.setCount(3));
            CompletableFuture<Map<String, String>> rpc = sim.acs.getParameterValues(
                    "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress");
            sim.start();

            CwmpMessage firstInform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(60));
            Map<String, String> values = rpc.get(60, TimeUnit.SECONDS);
            String ip = values.get(
                    "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress");
            assertNotNull(ip, "ExternalIPAddress should be populated from ${random.ip}");
            assertTrue(ip.startsWith("10."), "random.ip must be a 10.x.x.x address: " + ip);
            assertNotNull(firstInform);
        }
    }
}
