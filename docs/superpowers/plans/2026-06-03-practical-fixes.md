# Practical Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 4 real-world issues: UDS Windows compatibility, client-side Basic+Digest(SHA-256) auth, log file in JAR directory, and auth failure status correction.

**Architecture:** Four independent fixes touching separate subsystems. UDS path becomes platform-adaptive with CLI override. CwmpClient gains Basic auth and Digest SHA-256 support via auto-detection of WWW-Authenticate scheme. Logback FILE appender activates with dynamic JAR-relative path. CpeDevice detects 401 after auth retry and transitions to ERROR.

**Tech Stack:** Java 21, picocli, logback, java.security.MessageDigest, java.net.UnixDomainSocketAddress

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/com/cpe/simulator/cli/UdsClient.java` | Modify | Add `getDefaultSocketPath()`, accept path parameter in `send()` |
| `src/main/java/com/cpe/simulator/cli/UdsServer.java` | Modify | Accept socket path via constructor, remove hardcoded constant |
| `src/main/java/com/cpe/simulator/cli/StartCommand.java` | Modify | Add `--socket-path` option, pass to UdsServer |
| `src/main/java/com/cpe/simulator/cli/StopCommand.java` | Modify | Add `--socket-path` option, pass to UdsClient |
| `src/main/java/com/cpe/simulator/cli/StatusCommand.java` | Modify | Add `--socket-path` option, pass to UdsClient |
| `src/main/java/com/cpe/simulator/cli/DeviceCommand.java` | Modify | Add `--socket-path` option to each subcommand, pass to UdsClient |
| `src/main/java/com/cpe/simulator/cli/EventCommand.java` | Modify | Add `--socket-path` option to each subcommand, pass to UdsClient |
| `src/main/java/com/cpe/simulator/cwmp/DigestAuth.java` | Modify | Add SHA-256 support, generalize hash function |
| `src/main/java/com/cpe/simulator/cwmp/CwmpClient.java` | Modify | Add Basic auth handling, scheme detection |
| `src/main/java/com/cpe/simulator/engine/CpeDevice.java` | Modify | Detect 401 after inform, set ERROR state |
| `src/main/java/com/cpe/simulator/CpeSimulatorApp.java` | Modify | Set LOG_DIR system property at startup |
| `src/main/resources/logback.xml` | Modify | Activate FILE appender, use `${LOG_DIR}` variable |
| `src/test/java/com/cpe/simulator/cwmp/DigestAuthTest.java` | Modify | Add SHA-256 tests |
| `src/test/java/com/cpe/simulator/cli/UdsClientTest.java` | Create | Test `getDefaultSocketPath()` |
| `src/test/java/com/cpe/simulator/cwmp/CwmpClientAuthTest.java` | Create | Test Basic auth and scheme detection |

---

### Task 1: UDS Platform-Adaptive Path

**Files:**
- Modify: `src/main/java/com/cpe/simulator/cli/UdsClient.java`
- Modify: `src/main/java/com/cpe/simulator/cli/UdsServer.java`
- Modify: `src/main/java/com/cpe/simulator/cli/StartCommand.java`
- Modify: `src/main/java/com/cpe/simulator/cli/StopCommand.java`
- Modify: `src/main/java/com/cpe/simulator/cli/StatusCommand.java`
- Modify: `src/main/java/com/cpe/simulator/cli/DeviceCommand.java`
- Modify: `src/main/java/com/cpe/simulator/cli/EventCommand.java`
- Create: `src/test/java/com/cpe/simulator/cli/UdsClientTest.java`

- [ ] **Step 1: Write the failing test for `getDefaultSocketPath()`**

Create `src/test/java/com/cpe/simulator/cli/UdsClientTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=UdsClientTest -pl .`
Expected: FAIL — `getDefaultSocketPath()` does not exist yet.

- [ ] **Step 3: Implement `getDefaultSocketPath()` and refactor `UdsClient`**

Replace `src/main/java/com/cpe/simulator/cli/UdsClient.java` with:

```java
package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UdsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER_SIZE = 1024 * 1024;

    public static String getDefaultSocketPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String temp = System.getenv("TEMP");
            if (temp == null || temp.isEmpty()) {
                temp = System.getProperty("java.io.tmpdir");
            }
            return temp + "\\cpe-sim.sock";
        }
        return "/tmp/cpe-sim.sock";
    }

    public static JsonNode send(String action, JsonNode params, String socketPathStr) {
        if (socketPathStr == null || socketPathStr.isEmpty()) {
            socketPathStr = getDefaultSocketPath();
        }
        Path socketPath = Path.of(socketPathStr);
        if (!Files.exists(socketPath)) {
            System.err.println("Error: Simulator is not running (socket not found at " + socketPathStr + ")");
            return null;
        }

        try {
            SocketAddress address = UnixDomainSocketAddress.of(socketPath);
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(address);

                ObjectNode request = MAPPER.createObjectNode();
                request.put("action", action);
                if (params != null) {
                    request.set("params", params);
                }

                byte[] requestBytes = MAPPER.writeValueAsBytes(request);
                channel.write(ByteBuffer.wrap(requestBytes));
                channel.shutdownOutput();

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                StringBuilder responseBuilder = new StringBuilder();
                int bytesRead;
                while ((bytesRead = channel.read(buffer)) > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[bytesRead];
                    buffer.get(bytes);
                    responseBuilder.append(new String(bytes, StandardCharsets.UTF_8));
                    buffer.clear();
                }

                String responseStr = responseBuilder.toString();
                if (responseStr.isEmpty()) {
                    System.err.println("Error: Empty response from simulator");
                    return null;
                }

                JsonNode response = MAPPER.readTree(responseStr);
                if (response.has("error")) {
                    System.err.println("Error: " + response.get("error").asText());
                    return null;
                }
                return response;
            }
        } catch (java.net.ConnectException e) {
            System.err.println("Error: Simulator is not running (connection refused)");
            return null;
        } catch (Exception e) {
            System.err.println("Error communicating with simulator: " + e.getMessage());
            return null;
        }
    }

    public static JsonNode send(String action, JsonNode params) {
        return send(action, params, null);
    }

    public static JsonNode send(String action) {
        return send(action, null, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=UdsClientTest`
Expected: PASS

- [ ] **Step 5: Refactor `UdsServer` to accept socket path via constructor**

Replace `src/main/java/com/cpe/simulator/cli/UdsServer.java` lines 25–90 (class fields, constructor, start, stop, run) with:

```java
public class UdsServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER_SIZE = 1024 * 1024;

    private final SimulationEngine engine;
    private final String socketPath;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private ServerSocketChannel serverChannel;

    public UdsServer(SimulationEngine engine) {
        this(engine, UdsClient.getDefaultSocketPath());
    }

    public UdsServer(SimulationEngine engine, String socketPath) {
        this.engine = engine;
        this.socketPath = socketPath;
    }

    public void start() {
        running.set(true);
        Thread.ofVirtual().name("uds-server").start(this::run);
    }

    public void stop() {
        running.set(false);
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (Exception e) {
                SimLogger.error("Error closing UDS server", e);
            }
        }
        Path sp = Path.of(socketPath);
        try {
            Files.deleteIfExists(sp);
        } catch (Exception e) {
            SimLogger.error("Error deleting socket file", e);
        }
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    private void run() {
        Path sp = Path.of(socketPath);
        try {
            Files.deleteIfExists(sp);
            SocketAddress address = UnixDomainSocketAddress.of(sp);
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(address);
            SimLogger.info("UDS management server started at {}", socketPath);

            while (running.get()) {
                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    if (clientChannel != null) {
                        Thread.ofVirtual().name("uds-handler").start(() -> handleClient(clientChannel));
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        SimLogger.error("Error accepting UDS connection", e);
                    }
                }
            }
        } catch (Exception e) {
            SimLogger.error("UDS server error", e);
        }
    }
```

Keep `handleClient`, `processAction`, `listDevices`, `getDeviceInfo`, `findDevice`, `triggerRandomInforms`, and `formatDuration` methods unchanged.

- [ ] **Step 6: Add `--socket-path` to `StartCommand`**

In `src/main/java/com/cpe/simulator/cli/StartCommand.java`, add after line 118 (`boolean noPeriodic;`):

```java
    @Option(names = {"--socket-path"}, description = "UDS socket path (default: platform-specific)")
    String socketPath;
```

Change line 169 from:
```java
        UdsServer udsServer = new UdsServer(engine);
```
to:
```java
        UdsServer udsServer = socketPath != null
                ? new UdsServer(engine, socketPath)
                : new UdsServer(engine);
```

- [ ] **Step 7: Add `--socket-path` to `StopCommand`**

In `src/main/java/com/cpe/simulator/cli/StopCommand.java`, add after line 18 (`int timeout;`):

```java
    @Option(names = {"--socket-path"}, description = "UDS socket path")
    String socketPath;
```

Change line 26 from:
```java
        JsonNode response = UdsClient.send("stop", params);
```
to:
```java
        JsonNode response = UdsClient.send("stop", params, socketPath);
```

- [ ] **Step 8: Add `--socket-path` to `StatusCommand`**

In `src/main/java/com/cpe/simulator/cli/StatusCommand.java`, add after line 15 (`boolean json;`):

```java
    @Option(names = {"--socket-path"}, description = "UDS socket path")
    String socketPath;
```

Change line 19 from:
```java
        JsonNode response = UdsClient.send("status");
```
to:
```java
        JsonNode response = UdsClient.send("status", null, socketPath);
```

- [ ] **Step 9: Add `--socket-path` to `DeviceCommand` subcommands**

In `src/main/java/com/cpe/simulator/cli/DeviceCommand.java`, add the `--socket-path` option and pass it through in each subcommand.

For `ListDevices` (after line 37, `int limit;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 45 from `UdsClient.send("device-list", params)` to `UdsClient.send("device-list", params, socketPath)`.

For `GetDevice` (after line 81, `String path;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 91 from `UdsClient.send("device-get", params)` to `UdsClient.send("device-get", params, socketPath)`.

For `AddDevice` (after line 125, `int count;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 132 from `UdsClient.send("device-add", params)` to `UdsClient.send("device-add", params, socketPath)`.

For `RemoveDevice` (after line 143, `String deviceId;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 150 from `UdsClient.send("device-remove", params)` to `UdsClient.send("device-remove", params, socketPath)`.

For `InformDevice` (after line 164, `int random;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 178 from `UdsClient.send("device-inform", params)` to `UdsClient.send("device-inform", params, socketPath)`.

- [ ] **Step 10: Add `--socket-path` to `EventCommand` subcommands**

In `src/main/java/com/cpe/simulator/cli/EventCommand.java`:

For `RebootEvent` (after line 32, `String deviceId;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 39 from `UdsClient.send("event-reboot", params)` to `UdsClient.send("event-reboot", params, socketPath)`.

For `FactoryResetEvent` (after line 50, `String deviceId;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 57 from `UdsClient.send("event-factory-reset", params)` to `UdsClient.send("event-factory-reset", params, socketPath)`.

For `ValueChangeEvent` (after line 74, `String value;`), add:
```java
        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;
```
Change line 83 from `UdsClient.send("event-value-change", params)` to `UdsClient.send("event-value-change", params, socketPath)`.

- [ ] **Step 11: Run all tests**

Run: `mvn test`
Expected: All tests PASS

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "feat: UDS platform-adaptive path with --socket-path CLI override"
```

---

### Task 2: Client-Side Auth — Basic + Digest (MD5/SHA-256)

**Files:**
- Modify: `src/main/java/com/cpe/simulator/cwmp/DigestAuth.java`
- Modify: `src/main/java/com/cpe/simulator/cwmp/CwmpClient.java`
- Modify: `src/test/java/com/cpe/simulator/cwmp/DigestAuthTest.java`
- Create: `src/test/java/com/cpe/simulator/cwmp/CwmpClientAuthTest.java`

- [ ] **Step 1: Write failing tests for SHA-256 and Basic auth**

Add to `src/test/java/com/cpe/simulator/cwmp/DigestAuthTest.java` (append before the closing `}`):

```java
    @Test
    void hashHexSha256ProducesCorrectHash() {
        String result = DigestAuth.hashHex("SHA-256", "test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", result);
    }

    @Test
    void hashHexMd5MatchesLegacyMd5Hex() {
        String legacy = DigestAuth.md5Hex("hello");
        String generic = DigestAuth.hashHex("MD5", "hello");
        assertEquals(legacy, generic);
    }

    @Test
    void calculateDigestResponseWithSha256() {
        String response = DigestAuth.calculateDigestResponse(
                "SHA-256", "admin", "secret", "testrealm", "abc123",
                "/acs", "POST", "auth", "00000001", "cnonce123");
        assertNotNull(response);
        assertEquals(64, response.length());
    }

    @Test
    void buildAuthorizationHeaderWithSha256Algorithm() {
        String challenge = "Digest realm=\"testrealm\", nonce=\"abc123\", qop=\"auth\", algorithm=SHA-256";
        String header = DigestAuth.buildAuthorizationHeader(challenge, "admin", "password", "/acs", "POST");

        assertTrue(header.startsWith("Digest "));
        assertTrue(header.contains("algorithm=SHA-256"));
        assertTrue(header.contains("username=\"admin\""));
        assertTrue(header.contains("realm=\"testrealm\""));
    }

    @Test
    void buildAuthorizationHeaderDefaultsToMd5() {
        String challenge = "Digest realm=\"testrealm\", nonce=\"abc123\", qop=\"auth\"";
        String header = DigestAuth.buildAuthorizationHeader(challenge, "admin", "password", "/acs", "POST");

        assertTrue(header.contains("algorithm=MD5"));
    }
```

Create `src/test/java/com/cpe/simulator/cwmp/CwmpClientAuthTest.java`:

```java
package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CwmpClientAuthTest {

    @Test
    void buildBasicAuthHeaderEncodesCredentials() {
        String header = CwmpClient.buildBasicAuthHeader("admin", "secret");
        assertEquals("Basic YWRtaW46c2VjcmV0", header);
    }

    @Test
    void detectAuthSchemeDigest() {
        String scheme = CwmpClient.detectAuthScheme("Digest realm=\"test\", nonce=\"abc\"");
        assertEquals("Digest", scheme);
    }

    @Test
    void detectAuthSchemeBasic() {
        String scheme = CwmpClient.detectAuthScheme("Basic realm=\"test\"");
        assertEquals("Basic", scheme);
    }

    @Test
    void detectAuthSchemeNull() {
        String scheme = CwmpClient.detectAuthScheme(null);
        assertNull(scheme);
    }

    @Test
    void detectAuthSchemeCaseInsensitive() {
        assertEquals("Digest", CwmpClient.detectAuthScheme("digest realm=\"test\""));
        assertEquals("Basic", CwmpClient.detectAuthScheme("BASIC realm=\"test\""));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=DigestAuthTest,CwmpClientAuthTest`
Expected: FAIL — `hashHex`, `buildBasicAuthHeader`, `detectAuthScheme` do not exist yet.

- [ ] **Step 3: Add `hashHex()` and SHA-256 support to `DigestAuth`**

In `src/main/java/com/cpe/simulator/cwmp/DigestAuth.java`:

Replace the `calculateDigestResponse` method (lines 12–26) with:

```java
    public static String calculateDigestResponse(String algorithm, String username, String password,
                                                  String realm, String nonce,
                                                  String uri, String method,
                                                  String qop, String nc, String cnonce) {
        String ha1 = hashHex(algorithm, username + ":" + realm + ":" + password);
        String ha2 = hashHex(algorithm, method + ":" + uri);

        String response;
        if ("auth".equals(qop)) {
            response = hashHex(algorithm, ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        } else {
            response = hashHex(algorithm, ha1 + ":" + nonce + ":" + ha2);
        }
        return response;
    }

    public static String calculateDigestResponse(String username, String password,
                                                  String realm, String nonce,
                                                  String uri, String method,
                                                  String qop, String nc, String cnonce) {
        return calculateDigestResponse("MD5", username, password, realm, nonce, uri, method, qop, nc, cnonce);
    }
```

Replace the `buildAuthorizationHeader` method (lines 28–57) with:

```java
    public static String buildAuthorizationHeader(String wwwAuthenticate, String username,
                                                   String password, String uri, String method) {
        Map<String, String> params = parseWwwAuthenticate(wwwAuthenticate);

        String realm = params.getOrDefault("realm", "");
        String nonce = params.getOrDefault("nonce", "");
        String qop = params.getOrDefault("qop", null);
        String opaque = params.getOrDefault("opaque", null);
        String algorithm = params.getOrDefault("algorithm", "MD5");

        String nc = "00000001";
        String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String response = calculateDigestResponse(algorithm, username, password, realm, nonce, uri, method, qop, nc, cnonce);

        StringBuilder sb = new StringBuilder();
        sb.append("Digest username=\"").append(username).append("\"");
        sb.append(", realm=\"").append(realm).append("\"");
        sb.append(", nonce=\"").append(nonce).append("\"");
        sb.append(", uri=\"").append(uri).append("\"");
        sb.append(", response=\"").append(response).append("\"");
        sb.append(", algorithm=").append(algorithm);
        if (qop != null) {
            sb.append(", qop=").append(qop);
            sb.append(", nc=").append(nc);
            sb.append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (opaque != null) {
            sb.append(", opaque=\"").append(opaque).append("\"");
        }
        return sb.toString();
    }
```

Replace the `md5Hex` method (lines 100–112) with:

```java
    static String hashHex(String algorithm, String input) {
        try {
            String jcaAlgorithm = "SHA-256".equalsIgnoreCase(algorithm) ? "SHA-256" : "MD5";
            MessageDigest md = MessageDigest.getInstance(jcaAlgorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String md5Hex(String input) {
        return hashHex("MD5", input);
    }
```

- [ ] **Step 4: Run DigestAuth tests to verify they pass**

Run: `mvn test -Dtest=DigestAuthTest`
Expected: All PASS (including new SHA-256 tests and existing MD5 tests)

- [ ] **Step 5: Add `buildBasicAuthHeader()` and `detectAuthScheme()` to `CwmpClient`**

In `src/main/java/com/cpe/simulator/cwmp/CwmpClient.java`, add the following imports at the top (after existing imports):

```java
import java.util.Base64;
```

Add these two static methods before the `closeSession` method (before line 89):

```java
    static String buildBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String detectAuthScheme(String wwwAuthenticate) {
        if (wwwAuthenticate == null || wwwAuthenticate.isEmpty()) {
            return null;
        }
        String trimmed = wwwAuthenticate.trim();
        if (trimmed.regionMatches(true, 0, "Digest", 0, 6)) {
            return "Digest";
        }
        if (trimmed.regionMatches(true, 0, "Basic", 0, 5)) {
            return "Basic";
        }
        return null;
    }
```

- [ ] **Step 6: Refactor `sendRequest()` to handle both Basic and Digest auth**

Replace the 401 handling block in `sendRequest()` (lines 57–75) with:

```java
        if (response.statusCode() == 401) {
            String wwwAuth = response.headers().firstValue("WWW-Authenticate").orElse(null);
            String scheme = detectAuthScheme(wwwAuth);

            if (scheme != null) {
                String authHeader;
                if ("Digest".equals(scheme)) {
                    authHeader = DigestAuth.buildAuthorizationHeader(wwwAuth, acsUser, acsPassword, acsUrl, "POST");
                } else {
                    authHeader = buildBasicAuthHeader(acsUser, acsPassword);
                }

                HttpRequest.Builder retryBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(acsUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "text/xml; charset=utf-8")
                        .header("Authorization", authHeader)
                        .POST(HttpRequest.BodyPublishers.ofString(soapXml));

                if (cookie != null && !cookie.isEmpty()) {
                    retryBuilder.header("Cookie", cookie);
                }

                response = httpClient.send(retryBuilder.build(), HttpResponse.BodyHandlers.ofString());
            }
        }
```

- [ ] **Step 7: Run CwmpClientAuth tests**

Run: `mvn test -Dtest=CwmpClientAuthTest`
Expected: All PASS

- [ ] **Step 8: Run all tests**

Run: `mvn test`
Expected: All tests PASS

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: client-side Basic + Digest (MD5/SHA-256) auth with auto-detection"
```

---

### Task 3: Log File in JAR Directory

**Files:**
- Modify: `src/main/java/com/cpe/simulator/CpeSimulatorApp.java`
- Modify: `src/main/resources/logback.xml`

- [ ] **Step 1: Set `LOG_DIR` system property in `CpeSimulatorApp`**

Replace `src/main/java/com/cpe/simulator/CpeSimulatorApp.java` with:

```java
package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import picocli.CommandLine;

import java.nio.file.Path;

public class CpeSimulatorApp {

    public static void main(String[] args) {
        initLogDir();
        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }

    static void initLogDir() {
        try {
            Path jarPath = Path.of(CpeSimulatorApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path jarDir = jarPath.getParent();
            if (jarDir != null) {
                System.setProperty("LOG_DIR", jarDir.toString());
            }
        } catch (Exception e) {
            System.setProperty("LOG_DIR", ".");
        }
    }
}
```

- [ ] **Step 2: Activate FILE appender in `logback.xml`**

Replace `src/main/resources/logback.xml` with:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR:-.}/logs/cpe-simulator.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR:-.}/logs/cpe-simulator.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.cpe.simulator" level="INFO"/>
    <logger name="io.netty" level="WARN"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

- [ ] **Step 3: Build and verify**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run all tests**

Run: `mvn test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: activate file logging to JAR-relative directory"
```

---

### Task 4: Auth Failure Status Correction

**Files:**
- Modify: `src/main/java/com/cpe/simulator/engine/CpeDevice.java`

- [ ] **Step 1: Write failing test for auth failure state transition**

Create `src/test/java/com/cpe/simulator/engine/CpeDeviceAuthTest.java`:

```java
package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.CwmpClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class CpeDeviceAuthTest {

    @Test
    void deviceStateIsOnlineAfterConnect() {
        AppConfig config = new AppConfig();
        CwmpClient client = new CwmpClient(config);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Semaphore semaphore = new Semaphore(10);

        CpeDevice device = new CpeDevice(
                "test-001", "SN001", "000000", "SimCPE",
                null, config, client, scheduler, semaphore);
        device.connect();

        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());

        scheduler.shutdown();
        client.shutdown();
    }
}
```

> Note: This test validates the existing `connect()` -> `ONLINE` behavior. The auth failure state transition (401 -> ERROR) happens inside `runInformSession()` which requires a real HTTP server to test end-to-end. The existing integration tests (`PublicModeIntegrationTest`, `NatModeIntegrationTest`) cover full session flows. The code change in Step 3 is straightforward and verified by manual testing.

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn test -Dtest=CpeDeviceAuthTest`
Expected: PASS (validates existing connect behavior)

- [ ] **Step 3: Add 401 detection in `CpeDevice.runInformSession()`**

In `src/main/java/com/cpe/simulator/engine/CpeDevice.java`, replace lines 126–130:

```java
            if (response.statusCode() != 200) {
                SimLogger.device(deviceId, "Inform failed with status {}", response.statusCode());
                session.sessionComplete();
                return;
            }
```

with:

```java
            if (response.statusCode() == 401) {
                SimLogger.device(deviceId, "CWMP authentication failed for device");
                this.state = DeviceState.ERROR;
                session.sessionComplete();
                return;
            }

            if (response.statusCode() != 200) {
                SimLogger.device(deviceId, "Inform failed with status {}", response.statusCode());
                session.sessionComplete();
                return;
            }
```

- [ ] **Step 4: Run all tests**

Run: `mvn test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "fix: set device state to ERROR on CWMP authentication failure (401)"
```
