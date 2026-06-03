package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CwmpServerTest {

    private AppConfig config;
    private CwmpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        config = new AppConfig();
        config.setConnReqPort(port);
        config.setConnReqHost("127.0.0.1");
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static int findFreePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void serverStartsAndStops() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();
        assertTrue(server.isRunning());

        server.stop();
        Thread.sleep(200);
        assertFalse(server.isRunning());
    }

    @Test
    void connectionRequestReturns200ForKnownDevice() throws Exception {
        server = new CwmpServer(config, deviceId -> {
            if ("device-001".equals(deviceId)) {
                return new CwmpServer.ConnectionRequestResult(true, 200, "Accepted");
            }
            return new CwmpServer.ConnectionRequestResult(false, 404, "Device not found");
        });
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/device-001"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Accepted", response.body());
    }

    @Test
    void connectionRequestReturns404ForUnknownDevice() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(false, 404, "Device not found"));
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/unknown-device"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void connectionRequestReturns503ForOfflineDevice() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(false, 503, "Device offline"));
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/offline-device"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, response.statusCode());
    }

    @Test
    void nonGetRequestReturns404() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/device-001"))
                .POST(HttpRequest.BodyPublishers.ofString("body"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void authenticationRequiredWhenConfigured() throws Exception {
        config.setConnReqUser("admin");
        config.setConnReqPassword("secret");

        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/device-001"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    void authenticationSucceedsWithCorrectCredentials() throws Exception {
        config.setConnReqUser("admin");
        config.setConnReqPassword("secret");

        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();

        String credentials = Base64.getEncoder().encodeToString("admin:secret".getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/device-001"))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void authenticationFailsWithWrongCredentials() throws Exception {
        config.setConnReqUser("admin");
        config.setConnReqPassword("secret");

        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();

        String credentials = Base64.getEncoder().encodeToString("admin:wrong".getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/connReq/device-001"))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/other/path"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }
}
