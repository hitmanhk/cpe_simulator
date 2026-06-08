package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Tr098InformTest {

    private static class StubDeviceContext implements DeviceContext {
        private final DataModel dataModel;

        StubDeviceContext(DataModel dataModel) {
            this.dataModel = dataModel;
        }

        @Override public DataModel getDataModel() { return dataModel; }
        @Override public String getDeviceId() { return "test-device-001"; }
        @Override public String getSerialNumber() { return "SN12345"; }
        @Override public String getOui() { return "001122"; }
        @Override public String getProductClass() { return "SimCPE"; }
        @Override public AppConfig getConfig() { return new AppConfig(); }
        @Override public CwmpSession getSession() { return null; }
        @Override public void triggerInform(String eventCode) {}
        @Override public void scheduleReboot(int delaySeconds) {}
        @Override public void scheduleFactoryReset(int delaySeconds) {}
        @Override public void addPendingTransfer(CwmpSession.PendingTransfer transfer) {}
        @Override public java.util.List<CwmpSession.PendingTransfer> getPendingTransfers() { return java.util.List.of(); }
        @Override public void clearPendingTransfers() {}
        @Override public void connectXmpp() {}
    }

    @Test
    void tr098InformContainsInternetGatewayDevicePaths() {
        DataModel dm = ModelLoader.load("tr098");
        DeviceContext ctx = new StubDeviceContext(dm);
        InformHandler handler = new InformHandler();

        String informXml = handler.buildInform(ctx, "1 BOOT", "");

        assertTrue(informXml.contains("<Name>InternetGatewayDevice.DeviceInfo.Manufacturer</Name>"),
            "Inform should contain InternetGatewayDevice absolute paths");
        assertTrue(informXml.contains("<Name>InternetGatewayDevice.DeviceInfo.SoftwareVersion</Name>"),
            "Inform should contain InternetGatewayDevice absolute paths");
        assertTrue(informXml.contains("<Name>InternetGatewayDevice.ManagementServer.ConnectionRequestURL</Name>"),
            "Inform should contain InternetGatewayDevice absolute paths");
        assertFalse(informXml.contains("<Name>Device."),
            "Inform should NOT contain Device.* parameter names in TR-098 mode");
    }

    @Test
    void tr098InformHasNonEmptyManufacturerValue() {
        DataModel dm = ModelLoader.load("tr098");
        DeviceContext ctx = new StubDeviceContext(dm);
        InformHandler handler = new InformHandler();

        String informXml = handler.buildInform(ctx, "1 BOOT", "");

        assertTrue(informXml.contains("<Manufacturer>"),
            "Inform should have Manufacturer in DeviceIdStruct");
    }

    @Test
    void tr181InformContainsDevicePaths() {
        DataModel dm = ModelLoader.load("tr181");
        DeviceContext ctx = new StubDeviceContext(dm);
        InformHandler handler = new InformHandler();

        String informXml = handler.buildInform(ctx, "1 BOOT", "");

        assertTrue(informXml.contains("<Name>Device.DeviceInfo.Manufacturer</Name>"),
            "Inform should contain Device absolute paths for TR-181");
        assertTrue(informXml.contains("<Name>Device.ManagementServer.ConnectionRequestURL</Name>"),
            "Inform should contain Device absolute paths for TR-181");
        assertFalse(informXml.contains("<Name>InternetGatewayDevice."),
            "Inform should NOT contain InternetGatewayDevice paths in TR-181 mode");
    }
}