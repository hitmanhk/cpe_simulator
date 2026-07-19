package com.cpe.acsit.behavior;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.acs.CwmpRpc;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral coverage: reboot, factory reset (with default restore),
 * firmware download success/failure, scheduled inform, and periodic inform.
 */
class BehaviorIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void rebootRpcLeadsToBootInform() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> reboot = sim.acs.queueRpc(
                    CwmpRpc.reboot(CwmpRpc.nextId(), "reboot-1"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage rebootResponse = reboot.get(30, TimeUnit.SECONDS);
            assertFalse(rebootResponse.isFault());
            assertTrue(rebootResponse.rawXml().contains("RebootResponse"));

            CwmpMessage boot = sim.acs.awaitInformWithEvent("1 BOOT", Duration.ofSeconds(30));
            assertNotNull(boot.serialNumber());
        }
    }

    @Test
    void factoryResetRestoresDefaultsAndBoots() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> set = sim.acs.setParameterValues(Map.of(
                    "Device.ManagementServer.PeriodicInformInterval", "999"));
            CompletableFuture<CwmpMessage> reset = sim.acs.queueRpc(
                    CwmpRpc.factoryReset(CwmpRpc.nextId()));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            assertFalse(set.get(30, TimeUnit.SECONDS).isFault());
            assertFalse(reset.get(30, TimeUnit.SECONDS).isFault());

            // 重置在响应后异步执行（reboot-delay=1s）→ 1 BOOT 标志新状态就绪
            sim.acs.awaitInformWithEvent("1 BOOT", Duration.ofSeconds(30));
            SimHarness.awaitTrue(() -> sim.engine().getStatus().activeSessions() == 0,
                    Duration.ofSeconds(15), "session drained");

            // 新会话读取：必须看到恢复默认的 300，而非修改后的 999
            CompletableFuture<Map<String, String>> readBack = sim.acs.getParameterValues(
                    "Device.ManagementServer.PeriodicInformInterval");
            triggerConnectionRequest(sim.acs.getAllMessages().stream()
                    .filter(m -> "Inform".equals(m.method())).reduce((a, b) -> b).orElseThrow());
            sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST", Duration.ofSeconds(30));
            assertEquals("300", readBack.get(30, TimeUnit.SECONDS)
                    .get("Device.ManagementServer.PeriodicInformInterval"),
                    "factory reset must restore model defaults");
        }
    }

    @Test
    void firmwareDownloadCompletesAndUpgradesSoftwareVersion() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> download = sim.acs.queueRpc(CwmpRpc.download(
                    CwmpRpc.nextId(), "fw-dl", "http://files.example.com/fw-v2.bin",
                    "1 Firmware Upgrade Image", 2048, 0));
            sim.start();
            CwmpMessage bootstrap = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertFalse(download.get(30, TimeUnit.SECONDS).isFault());

            // download-delay=1s：等 transfer 完成落库
            Thread.sleep(2500);
            SimHarness.awaitTrue(() -> sim.engine().getStatus().activeSessions() == 0,
                    Duration.ofSeconds(15), "session drained");

            // 通过 HTTP 回连触发新会话查询结果
            CompletableFuture<CwmpMessage> transfers = sim.acs.queueRpc(
                    CwmpRpc.getQueuedTransfers(CwmpRpc.nextId()));
            CompletableFuture<Map<String, String>> swVersion = sim.acs.getParameterValues(
                    "Device.DeviceInfo.SoftwareVersion");
            triggerConnectionRequest(bootstrap);

            sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST", Duration.ofSeconds(30));
            CwmpMessage transfersResponse = transfers.get(30, TimeUnit.SECONDS);
            assertEquals(1, transfersResponse.transfers().getOrDefault("fw-dl", -1),
                    "completed download should report state=1");

            String version = swVersion.get(30, TimeUnit.SECONDS)
                    .get("Device.DeviceInfo.SoftwareVersion");
            assertTrue(version != null && version.startsWith("UPGRADED-"),
                    "firmware image success should bump SoftwareVersion: " + version);
        }
    }

    @Test
    void downloadFailureReportsFaultState() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.configure(c -> c.setDownloadSuccessRate(0.0));
            CompletableFuture<CwmpMessage> download = sim.acs.queueRpc(CwmpRpc.download(
                    CwmpRpc.nextId(), "fw-fail", "http://files.example.com/broken.bin",
                    "1 Firmware Upgrade Image", 512, 0));
            sim.start();
            CwmpMessage bootstrap = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertFalse(download.get(30, TimeUnit.SECONDS).isFault());

            Thread.sleep(2500);
            SimHarness.awaitTrue(() -> sim.engine().getStatus().activeSessions() == 0,
                    Duration.ofSeconds(15), "session drained");

            CompletableFuture<CwmpMessage> transfers = sim.acs.queueRpc(
                    CwmpRpc.getQueuedTransfers(CwmpRpc.nextId()));
            triggerConnectionRequest(bootstrap);
            sim.acs.awaitInformWithEvent("6 CONNECTION REQUEST", Duration.ofSeconds(30));

            CwmpMessage transfersResponse = transfers.get(30, TimeUnit.SECONDS);
            assertEquals(4, transfersResponse.transfers().getOrDefault("fw-fail", -1),
                    "failed download should report state=4");
        }
    }

    @Test
    void scheduleInformFiresAfterDelay() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> schedule = sim.acs.queueRpc(
                    CwmpRpc.scheduleInform(CwmpRpc.nextId(), 2, "sched-key"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));
            assertFalse(schedule.get(30, TimeUnit.SECONDS).isFault());

            CwmpMessage scheduled = sim.acs.awaitInformWithEvent("M ScheduleInform",
                    Duration.ofSeconds(30));
            assertNotNull(scheduled.serialNumber());
        }
    }

    @Test
    void periodicInformRepeatsAtConfiguredInterval() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            sim.configure(c -> c.getInformSchedules().getPeriodic().setInterval(2));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            sim.acs.awaitInformWithEvent("2 PERIODIC", Duration.ofSeconds(30));
            sim.acs.awaitInformWithEvent("2 PERIODIC", Duration.ofSeconds(30));
            long periodicCount = sim.acs.getAllMessages().stream()
                    .filter(m -> "Inform".equals(m.method()))
                    .filter(m -> m.eventCodes().stream().anyMatch(e -> e.startsWith("2 PERIODIC")))
                    .count();
            assertTrue(periodicCount >= 2, "expected at least 2 periodic informs, got " + periodicCount);
        }
    }

    private void triggerConnectionRequest(CwmpMessage inform) throws Exception {
        Map<String, String> params = inform.parameterValues();
        String url = params.get("Device.ManagementServer.ConnectionRequestURL");
        assertNotNull(url, "device must advertise a ConnectionRequestURL");
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
