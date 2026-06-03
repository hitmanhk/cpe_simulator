# CPE Simulator Practical Fixes Design

Date: 2026-06-03

Four issues discovered during real-world usage, addressed in a single focused change.

---

## Issue 1: UDS Platform-Adaptive Path

### Problem

Socket path is hardcoded to `/tmp/cpe-sim.sock` in both `UdsServer.java:27` and `UdsClient.java:18`. On Windows, the `/tmp` directory does not exist, causing "socket not found" errors.

### Design

**Approach:** Platform-adaptive default path with CLI override.

- Add a static utility method `UdsClient.getDefaultSocketPath()` that detects the OS at runtime:
  - Linux/macOS: `/tmp/cpe-sim.sock`
  - Windows: `%TEMP%\cpe-sim.sock` (via `System.getenv("TEMP")`)
- Replace the `private static final String SOCKET_PATH` constants in both `UdsServer` and `UdsClient` with runtime-resolved values.
- Add `--socket-path` CLI option to all commands (`start`, `stop`, `status`, `device`, `event`) so users can override the default.
- When `--socket-path` is not provided, both server and client call `getDefaultSocketPath()` to ensure consistency.
- If the platform does not support Unix Domain Sockets (very old Windows), fail at startup with a clear error message rather than a cryptic `UnsupportedOperationException`.

### Files Changed

| File | Change |
|------|--------|
| `UdsServer.java` | Replace hardcoded path with constructor-injected path; accept path parameter |
| `UdsClient.java` | Replace hardcoded path; `send()` accepts path parameter |
| `StartCommand.java` | Add `--socket-path` option; pass to `UdsServer` |
| `StopCommand.java` | Add `--socket-path` option; pass to `UdsClient` |
| `StatusCommand.java` | Add `--socket-path` option; pass to `UdsClient` |
| `DeviceCommand.java` | Add `--socket-path` option; pass to `UdsClient` |
| `EventCommand.java` | Add `--socket-path` option; pass to `UdsClient` |

---

## Issue 2: Client-Side Auth Support (Basic + Digest MD5/SHA-256)

### Problem

`CwmpClient` only handles `WWW-Authenticate: Digest` challenges with MD5. If the ACS responds with `WWW-Authenticate: Basic`, the client silently ignores it and returns 401. No SHA-256 support.

### Design

**Approach:** Auto-detect auth scheme from server challenge; support Basic and Digest (MD5 + SHA-256).

**Auth flow after receiving 401:**

1. Parse `WWW-Authenticate` header from the 401 response.
2. Determine scheme:
   - Starts with `Digest` (case-insensitive) -> Digest auth path
   - Starts with `Basic` (case-insensitive) -> Basic auth path
   - If multiple challenges present, prefer Digest over Basic.
3. **Basic auth:** Construct `Authorization: Basic base64(user:pass)` and retry.
4. **Digest auth:**
   - Parse `algorithm` field from challenge. Supported values: `MD5` (default if absent), `SHA-256`.
   - Use the specified algorithm for HA1 and HA2 hash computation.
   - Include `algorithm=<value>` in the Authorization response header.
   - Keep existing qop=auth, cnonce generation, and nc=00000001 logic.

**DigestAuth.java changes:**

- Generalize `md5Hex()` to `hashHex(String algorithm, String input)` supporting MD5 and SHA-256 via `java.security.MessageDigest`.
- `calculateDigestResponse()` accepts an `algorithm` parameter.
- `buildAuthorizationHeader()` parses the `algorithm` field from the challenge and passes it through.
- The generated Authorization header includes `algorithm=MD5` or `algorithm=SHA-256` as appropriate.

**CwmpClient.java changes:**

- After receiving 401, inspect `WWW-Authenticate` header prefix to determine scheme.
- Add Basic auth branch: encode credentials as Base64 and retry.
- Refactor Digest branch to use updated `DigestAuth` with algorithm support.
- Preserve cookie handling across both auth paths.

### Files Changed

| File | Change |
|------|--------|
| `DigestAuth.java` | Add SHA-256 support; generalize hash function; parse algorithm from challenge |
| `CwmpClient.java` | Add Basic auth handling; scheme detection; pass algorithm to DigestAuth |
| `DigestAuthTest.java` | Add tests for SHA-256, Basic auth, scheme detection |

---

## Issue 3: Log File in JAR Directory

### Problem

The `FILE` appender in `logback.xml` is defined but never activated (not referenced in `<root>`). Logs only go to STDOUT. The file path `logs/cpe-simulator.log` is relative to CWD, not the JAR location.

### Design

**Approach:** Activate FILE appender with dynamic path based on JAR directory.

- At application startup (earliest point in `CpeSimulatorApp.main()`), determine the JAR directory:
  ```
  Path jarDir = Path.of(CpeSimulatorApp.class.getProtectionDomain()
      .getCodeSource().getLocation().toURI()).getParent();
  ```
- Set system property: `System.setProperty("LOG_DIR", jarDir.toString())`.
- Update `logback.xml`:
  - FILE appender file: `${LOG_DIR:-.}/logs/cpe-simulator.log`
  - Rolling pattern: `${LOG_DIR:-.}/logs/cpe-simulator.%d{yyyy-MM-dd}.log`
  - Add `<appender-ref ref="FILE"/>` to `<root>` element.
- Keep STDOUT appender active.
- Keep existing logger levels: `com.cpe.simulator` = INFO, `io.netty` = WARN.
- Keep rolling policy: daily rotation, 7-day retention.

### Files Changed

| File | Change |
|------|--------|
| `CpeSimulatorApp.java` | Set `LOG_DIR` system property at startup |
| `logback.xml` | Activate FILE appender; use `${LOG_DIR}` variable in paths |

---

## Issue 4: Auth Failure Shows ONLINE Instead of ERROR

### Problem

In public mode, `CpeDevice.connect()` immediately sets state to `ONLINE`. When the subsequent CWMP Inform session fails authentication (401 persists after retry), only a log message is emitted. Device state remains `ONLINE`.

### Design

**Approach:** Detect auth failure in the CWMP session and set device state to `ERROR`, consistent with XMPP SASL failure behavior.

**CwmpClient changes:**

- `sendRequest()` return type needs to distinguish auth failure from other HTTP errors. Options:
  - Return a result object with `statusCode` and `authFailed` flag.
  - Or: the caller checks if the final response is 401 after the retry logic completes.
- Preferred: caller checks final HTTP status. If 401 persists after auth retry, it is an auth failure.

**CpeDevice changes:**

- In `runInformSession()`, after `CwmpClient.sendRequest()` returns, check if the final HTTP status is 401.
- If 401 (auth failed): set `state = DeviceState.ERROR`, log `"CWMP authentication failed for device {id}"`.
- If other non-200: keep existing behavior (log failure, session complete, state unchanged for transient errors).
- Exception path: keep existing `state = ERROR` behavior.

**No changes to:**

- `DeviceState` enum (reuse `ERROR`).
- `connect()` method (still sets `ONLINE` to confirm network connectivity).
- Health check / reconnect logic (already handles `ERROR` state in NAT mode; public mode reconnect is a separate concern).

### Files Changed

| File | Change |
|------|--------|
| `CpeDevice.java` | Check for 401 after sendRequest; set ERROR on auth failure |
| `CwmpClient.java` | Ensure 401 status is visible to caller after auth retry |

---

## Testing

- **UDS:** Unit test for `getDefaultSocketPath()` covering Linux/macOS/Windows paths. Integration test verifying server-client communication with custom path.
- **Auth:** Unit tests for Digest SHA-256 hash computation, Basic auth header construction, scheme detection from WWW-Authenticate header. Update existing `DigestAuthTest`.
- **Logging:** Manual verification that log file appears in JAR directory. Verify `${LOG_DIR}` fallback works when property is unset.
- **Auth failure status:** Unit test simulating 401 response from ACS, verifying device transitions to ERROR state.
