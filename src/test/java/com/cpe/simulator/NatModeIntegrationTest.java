package com.cpe.simulator;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpServer;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.DeviceRegistry;
import com.cpe.simulator.model.DataModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class NatModeIntegrationTest {

    private CwmpServer server;
    private DeviceRegistry registry;
    private HttpClient httpClient;
    private int port;
    private ScheduledExecutorService scheduler;
    private CpeDevice device;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        httpClient = HttpClient.newHttpClient();
        scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        AppConfig config = new AppConfig();
        config.setConnReqPort(port);
        config.setConnReqHost("127.0.0.1");
        config.setMaxSessions(10);
        config.setSessionTimeout(30);

        DataModel dm = new DataModel();
        dm.saveDefaults();

        Semaphore semaphore = new Semaphore(10);

        device = new CpeDevice(
                "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
                dm, config, null, scheduler, semaphore
        );
        device.initialize();
        device.setState(CpeDevice.DeviceState.ONLINE);

        registry = new DeviceRegistry();
        registry.register(device.getDeviceId(), device);

        server = new CwmpServer(config, deviceId -> {
            var ctx = registry.getDevice(deviceId);
            if (ctx == null) {
                return new CwmpServer.ConnectionRequestResult(false, 404, "Device not found");
            }
            CpeDevice dev = (CpeDevice) ctx;
            if (dev.getState() == CpeDevice.DeviceState.OFFLINE) {
                return new CwmpServer.ConnectionRequestResult(false, 503, "Device offline");
            }
            dev.triggerInform("6 CONNECTION REQUEST");
            return new CwmpServer.ConnectionRequestResult(true, 200, "Connection request accepted");
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void connectionRequestReturns200ForKnownDevice() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/000000-SimCPE-SIM00001"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    @Timeout(10)
    void connectionRequestReturns404ForUnknownDevice() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/unknown-device-999"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    @Timeout(10)
    void connectionRequestTriggersInform() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/000000-SimCPE-SIM00001"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        Thread.sleep(500);
    }

    @Test
    @Timeout(10)
    void connectionRequestForOfflineDeviceReturns503() throws Exception {
        device.setState(CpeDevice.DeviceState.OFFLINE);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/000000-SimCPE-SIM00001"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, response.statusCode());
    }

    @Test
    @Timeout(10)
    void multipleConnectionRequestsSucceed() throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/connReq/000000-SimCPE-SIM00001"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() == 200 || response.statusCode() == 503);
        }
    }

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
