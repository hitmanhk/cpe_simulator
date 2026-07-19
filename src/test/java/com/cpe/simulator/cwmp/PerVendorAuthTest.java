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

/**
 * Per-vendor connection request authentication on the HTTP path
 * (CwmpServerHandler): device-specific credentials from the provider win,
 * global config is the fallback, and no credentials means open access.
 */
class PerVendorAuthTest {

    private AppConfig config;
    private CwmpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        config = new AppConfig();
        config.setConnReqHost("127.0.0.1");
        port = freePort();
        config.setConnReqPort(port);
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void vendorCredentialsAreRequiredForVendorDevice() throws Exception {
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.setCredentialProvider(deviceId -> "dev-hw-1".equals(deviceId)
                ? new String[]{"hwuser", "hwpass"} : null);
        server.start();

        // 无凭据 → 401
        assertEquals(401, get("/connReq/dev-hw-1", null));
        // 全局凭据（未配置）之外的其他凭据 → 401
        assertEquals(401, get("/connReq/dev-hw-1", "hwuser:wrong"));
        // 厂商凭据正确 → 200
        assertEquals(200, get("/connReq/dev-hw-1", "hwuser:hwpass"));
        // 无厂商凭据的设备且全局未启用 → 放行
        assertEquals(200, get("/connReq/dev-other", null));
    }

    @Test
    void fallsBackToGlobalCredentials() throws Exception {
        config.setConnReqUser("global");
        config.setConnReqPassword("globalpass");
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.setCredentialProvider(deviceId -> null); // 全部回退全局
        server.start();

        assertEquals(401, get("/connReq/dev-1", null));
        assertEquals(200, get("/connReq/dev-1", "global:globalpass"));
        assertEquals(401, get("/connReq/dev-1", "global:bad"));
    }

    @Test
    void vendorCredentialsWinOverGlobal() throws Exception {
        config.setConnReqUser("global");
        config.setConnReqPassword("globalpass");
        server = new CwmpServer(config, deviceId ->
                new CwmpServer.ConnectionRequestResult(true, 200, "OK"));
        server.setCredentialProvider(deviceId -> "dev-v".equals(deviceId)
                ? new String[]{"vendor", "vendorpass"} : null);
        server.start();

        // 厂商设备：全局凭据不再适用，必须用厂商凭据
        assertEquals(401, get("/connReq/dev-v", "global:globalpass"));
        assertEquals(200, get("/connReq/dev-v", "vendor:vendorpass"));
        // 非厂商设备：全局凭据
        assertEquals(200, get("/connReq/dev-g", "global:globalpass"));
    }

    private int get(String path, String basicCredentials) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (basicCredentials != null) {
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basicCredentials.getBytes()));
        }
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
