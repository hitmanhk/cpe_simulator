package com.cpe.acsit.connreq;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP Connection Request coverage (public mode): URL routing, Basic
 * authentication, unknown-device 404, and the resulting 6 CONNECTION REQUEST
 * inform.
 */
class ConnectionRequestIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void connectionRequestTriggersCwmpSession() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.start();
            CwmpMessage bootstrap = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            String connReqUrl = connReqUrlOf(bootstrap);
            assertNotNull(connReqUrl);

            // 等会话排空（204 结束），再发回连
            SimHarness.awaitTrue(() -> sim.engine().getStatus().activeSessions() == 0,
                    Duration.ofSeconds(15), "session drained");

            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(connReqUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            CwmpMessage triggered = sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST",
                    Duration.ofSeconds(30));
            assertEquals(bootstrap.serialNumber(), triggered.serialNumber());
        }
    }

    @Test
    void unknownDeviceReturns404() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + sim.config.getConnReqPort() + "/connReq/no-such-device"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void connectionRequestAuthEnforced() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.configure(c -> {
                c.setConnReqUser("cruser");
                c.setConnReqPassword("crpass");
            });
            sim.start();
            CwmpMessage bootstrap = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            String connReqUrl = connReqUrlOf(bootstrap);

            SimHarness.awaitTrue(() -> sim.engine().getStatus().activeSessions() == 0,
                    Duration.ofSeconds(15), "session drained");

            HttpResponse<String> unauthenticated = http.send(
                    HttpRequest.newBuilder(URI.create(connReqUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthenticated.statusCode());
            assertNotNull(unauthenticated.headers().firstValue("WWW-Authenticate").orElse(null));

            String basic = "Basic " + Base64.getEncoder().encodeToString("cruser:crpass".getBytes());
            HttpResponse<String> authenticated = http.send(
                    HttpRequest.newBuilder(URI.create(connReqUrl))
                            .header("Authorization", basic).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authenticated.statusCode());

            CwmpMessage triggered = sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST",
                    Duration.ofSeconds(30));
            assertEquals(bootstrap.serialNumber(), triggered.serialNumber());
        }
    }

    private static String connReqUrlOf(CwmpMessage inform) {
        Map<String, String> params = inform.parameterValues();
        return params.get("Device.ManagementServer.ConnectionRequestURL") != null
                ? params.get("Device.ManagementServer.ConnectionRequestURL")
                : params.get("InternetGatewayDevice.ManagementServer.ConnectionRequestURL");
    }
}
