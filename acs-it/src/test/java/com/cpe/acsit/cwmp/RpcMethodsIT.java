package com.cpe.acsit.cwmp;

import com.cpe.acsit.acs.CwmpMessage;
import com.cpe.acsit.acs.CwmpRpc;
import com.cpe.acsit.harness.SimHarness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Happy-path coverage of every CWMP RPC the simulator implements.
 * All requests are queued before engine start so they are driven down a
 * single CWMP session in order, exactly like a real ACS would pipeline them.
 */
class RpcMethodsIT {

    private static final Set<String> EXPECTED_METHODS = Set.of(
            "GetParameterValues", "SetParameterValues", "GetParameterNames",
            "GetParameterAttributes", "SetParameterAttributes", "AddObject", "DeleteObject",
            "Download", "Reboot", "FactoryReset", "ScheduleInform",
            "GetQueuedTransfers", "GetAllQueuedTransfers", "GetRPCMethods",
            "GetOptions", "SetVouchers", "Upload");

    @Test
    void getRpcMethodsReturnsAllSeventeen() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> rpc = sim.acs.queueRpc(CwmpRpc.getRpcMethods(CwmpRpc.nextId()));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage response = rpc.get(30, TimeUnit.SECONDS);
            assertFalse(response.isFault());
            assertEquals(EXPECTED_METHODS, Set.copyOf(response.methodList()),
                    "GetRPCMethods must list exactly the implemented methods");
        }
    }

    @Test
    void getParameterValuesSupportsFullAndPartialPaths() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<Map<String, String>> leaf = sim.acs.getParameterValues(
                    "Device.DeviceInfo.SerialNumber", "Device.ManagementServer.URL");
            CompletableFuture<Map<String, String>> partial = sim.acs.getParameterValues("Device.DeviceInfo.");
            sim.start();
            CwmpMessage inform = sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            Map<String, String> leafValues = leaf.get(30, TimeUnit.SECONDS);
            assertEquals(inform.serialNumber(), leafValues.get("Device.DeviceInfo.SerialNumber"));
            assertEquals(sim.config.getAcsUrl(), leafValues.get("Device.ManagementServer.URL"));

            Map<String, String> partialValues = partial.get(30, TimeUnit.SECONDS);
            assertTrue(partialValues.size() >= 8,
                    "partial path should expand to the whole DeviceInfo subtree: " + partialValues.size());
            assertTrue(partialValues.keySet().stream()
                    .allMatch(k -> k.startsWith("Device.DeviceInfo.")));
        }
    }

    @Test
    void setParameterValuesAppliesWritableParameters() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> set = sim.acs.setParameterValues(Map.of(
                    "Device.ManagementServer.PeriodicInformInterval", "600"));
            CompletableFuture<Map<String, String>> readBack = sim.acs.getParameterValues(
                    "Device.ManagementServer.PeriodicInformInterval");
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage setResponse = set.get(30, TimeUnit.SECONDS);
            assertFalse(setResponse.isFault());
            assertEquals(0, setResponse.intField("Status", -1));
            assertEquals("600", readBack.get(30, TimeUnit.SECONDS)
                    .get("Device.ManagementServer.PeriodicInformInterval"));
        }
    }

    @Test
    void getParameterNamesHonoursNextLevel() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            // Device.WiFi. 下有多张对象表（SSID./Radio./AccessPoint.），适合验证层级语义
            CompletableFuture<CwmpMessage> nextLevel = sim.acs.queueRpc(
                    CwmpRpc.getParameterNames(CwmpRpc.nextId(), "Device.WiFi.", true));
            CompletableFuture<CwmpMessage> fullTree = sim.acs.queueRpc(
                    CwmpRpc.getParameterNames(CwmpRpc.nextId(), "Device.WiFi.", false));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            Map<String, Boolean> nextLevelNames = nextLevel.get(30, TimeUnit.SECONDS).parameterNames();
            assertFalse(nextLevelNames.isEmpty());
            // NextLevel: 只允许直接子级（叶子参数或一层对象节点）
            for (String name : nextLevelNames.keySet()) {
                String rest = name.substring("Device.WiFi.".length());
                String trimmed = rest.endsWith(".") ? rest.substring(0, rest.length() - 1) : rest;
                assertFalse(trimmed.contains("."),
                        "NextLevel result must not contain grandchildren: " + name);
            }

            Map<String, Boolean> fullNames = fullTree.get(30, TimeUnit.SECONDS).parameterNames();
            assertTrue(fullNames.size() > nextLevelNames.size(),
                    "full tree should contain more entries than NextLevel: full="
                            + fullNames.size() + " nextLevel=" + nextLevelNames.size());
            assertTrue(fullNames.containsKey("Device.WiFi.SSID.1.SSID"));
        }
    }

    @Test
    void parameterAttributesRoundTrip() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            String path = "Device.ManagementServer.PeriodicInformInterval";
            CompletableFuture<CwmpMessage> before = sim.acs.queueRpc(
                    CwmpRpc.getParameterAttributes(CwmpRpc.nextId(), path));
            CompletableFuture<CwmpMessage> set = sim.acs.queueRpc(
                    CwmpRpc.setParameterAttributes(CwmpRpc.nextId(), path, true, 2, false));
            CompletableFuture<CwmpMessage> after = sim.acs.queueRpc(
                    CwmpRpc.getParameterAttributes(CwmpRpc.nextId(), path));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            assertEquals(0, before.get(30, TimeUnit.SECONDS).parameterAttributes().get(path));
            assertFalse(set.get(30, TimeUnit.SECONDS).isFault());
            assertEquals(2, after.get(30, TimeUnit.SECONDS).parameterAttributes().get(path),
                    "notification change must persist");
        }
    }

    @Test
    void addAndDeleteObjectOnMultiInstanceTable() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            String table = "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.";
            CompletableFuture<CwmpMessage> add = sim.acs.queueRpc(
                    CwmpRpc.addObject(CwmpRpc.nextId(), table, "key-1"));
            CompletableFuture<CwmpMessage> names = sim.acs.queueRpc(
                    CwmpRpc.getParameterNames(CwmpRpc.nextId(), table, false));
            CompletableFuture<CwmpMessage> delete = sim.acs.queueRpc(
                    CwmpRpc.deleteObject(CwmpRpc.nextId(), table + "2.", "key-2"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage addResponse = add.get(30, TimeUnit.SECONDS);
            assertFalse(addResponse.isFault());
            assertEquals(2, addResponse.intField("InstanceNumber", -1),
                    "second instance should get number 2");
            assertEquals(0, addResponse.intField("Status", -1));

            assertTrue(names.get(30, TimeUnit.SECONDS).parameterNames().keySet().stream()
                    .anyMatch(n -> n.startsWith(table + "2.")));

            CwmpMessage deleteResponse = delete.get(30, TimeUnit.SECONDS);
            assertFalse(deleteResponse.isFault());
            assertEquals(0, deleteResponse.intField("Status", -1));
        }
    }

    @Test
    void instanceNumbersAreReusedAfterDelete() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            String table = "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.";
            CompletableFuture<CwmpMessage> add = sim.acs.queueRpc(CwmpRpc.addObject(CwmpRpc.nextId(), table, "k"));
            CompletableFuture<CwmpMessage> delete = sim.acs.queueRpc(CwmpRpc.deleteObject(CwmpRpc.nextId(), table + "2.", "k"));
            CompletableFuture<CwmpMessage> addAgain = sim.acs.queueRpc(CwmpRpc.addObject(CwmpRpc.nextId(), table, "k"));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            assertEquals(2, add.get(30, TimeUnit.SECONDS).intField("InstanceNumber", -1));
            assertFalse(delete.get(30, TimeUnit.SECONDS).isFault());
            assertEquals(2, addAgain.get(30, TimeUnit.SECONDS).intField("InstanceNumber", -1),
                    "deleted instance number should be reused");
        }
    }

    @Test
    void downloadAnswersImmediatelyWithStatusZero() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> download = sim.acs.queueRpc(CwmpRpc.download(
                    CwmpRpc.nextId(), "dl-1", "http://files.example.com/fw.bin",
                    "1 Firmware Upgrade Image", 1000, 0));
            CompletableFuture<CwmpMessage> transfers = sim.acs.queueRpc(
                    CwmpRpc.getAllQueuedTransfers(CwmpRpc.nextId()));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            CwmpMessage downloadResponse = download.get(30, TimeUnit.SECONDS);
            assertFalse(downloadResponse.isFault());
            assertEquals(0, downloadResponse.intField("Status", -1));
            assertNotNull(downloadResponse.field("StartTime"));

            // 查询结构合法（下载结果 1s 后异步完成，完成语义见 BehaviorIT）
            CwmpMessage transfersResponse = transfers.get(30, TimeUnit.SECONDS);
            assertFalse(transfersResponse.isFault());
            assertNotNull(transfersResponse.transfers());
        }
    }

    @Test
    void scheduleInformGetOptionsSetVouchersUploadRespond() throws Exception {
        try (SimHarness sim = new SimHarness()) {
            CompletableFuture<CwmpMessage> schedule = sim.acs.queueRpc(
                    CwmpRpc.scheduleInform(CwmpRpc.nextId(), 3600, "sched-1"));
            CompletableFuture<CwmpMessage> options = sim.acs.queueRpc(
                    CwmpRpc.getOptions(CwmpRpc.nextId(), "opt-1"));
            CompletableFuture<CwmpMessage> vouchers = sim.acs.queueRpc(
                    CwmpRpc.setVouchers(CwmpRpc.nextId(), "dmFsdWU="));
            CompletableFuture<CwmpMessage> upload = sim.acs.queueRpc(CwmpRpc.upload(
                    CwmpRpc.nextId(), "ul-1", "http://files.example.com/log",
                    "2 Vendor Configuration File", 0));
            CompletableFuture<CwmpMessage> queuedTransfers = sim.acs.queueRpc(
                    CwmpRpc.getQueuedTransfers(CwmpRpc.nextId()));
            sim.start();
            sim.acs.awaitInformWithEvent("0 BOOTSTRAP", Duration.ofSeconds(30));

            assertFalse(schedule.get(30, TimeUnit.SECONDS).isFault());
            CwmpMessage optionsResponse = options.get(30, TimeUnit.SECONDS);
            assertFalse(optionsResponse.isFault());
            assertTrue(optionsResponse.rawXml().contains("GetOptionsResponse"));
            assertFalse(vouchers.get(30, TimeUnit.SECONDS).isFault());
            CwmpMessage uploadResponse = upload.get(30, TimeUnit.SECONDS);
            assertFalse(uploadResponse.isFault());
            assertEquals(0, uploadResponse.intField("Status", -1));
            assertFalse(queuedTransfers.get(30, TimeUnit.SECONDS).isFault());
        }
    }
}
