package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RpcHandlerTest {

    @Mock
    private DeviceContext device;

    private DataModel dataModel;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        dataModel = new DataModel();
        config = new AppConfig();

        ParameterNode deviceNode = new ParameterNode("Device", "Device");
        ParameterNode deviceInfoNode = new ParameterNode("DeviceInfo", "Device.DeviceInfo");

        ParameterNode manufacturerNode = new ParameterNode("Manufacturer", "Device.DeviceInfo.Manufacturer");
        manufacturerNode.setValue(new ParameterValue("TestMfg", "xsd:string"));
        manufacturerNode.setWritable(false);

        ParameterNode softwareVersionNode = new ParameterNode("SoftwareVersion", "Device.DeviceInfo.SoftwareVersion");
        softwareVersionNode.setValue(new ParameterValue("1.0.0", "xsd:string"));
        softwareVersionNode.setWritable(true);

        ParameterNode provisioningCodeNode = new ParameterNode("ProvisioningCode", "Device.DeviceInfo.ProvisioningCode");
        provisioningCodeNode.setValue(new ParameterValue("", "xsd:string"));
        provisioningCodeNode.setWritable(true);

        ParameterNode upTimeNode = new ParameterNode("UpTime", "Device.DeviceInfo.UpTime");
        upTimeNode.setValue(new ParameterValue("12345", "xsd:unsignedInt"));
        upTimeNode.setWritable(false);

        ParameterNode retryCountNode = new ParameterNode("RetryCount", "Device.DeviceInfo.RetryCount");
        retryCountNode.setValue(new ParameterValue("0", "xsd:unsignedInt"));
        retryCountNode.setWritable(true);

        deviceInfoNode.addChild(manufacturerNode);
        deviceInfoNode.addChild(softwareVersionNode);
        deviceInfoNode.addChild(provisioningCodeNode);
        deviceInfoNode.addChild(upTimeNode);
        deviceInfoNode.addChild(retryCountNode);
        deviceNode.addChild(deviceInfoNode);

        ParameterNode mgmtServerNode = new ParameterNode("ManagementServer", "Device.ManagementServer");

        ParameterNode connReqUrlNode = new ParameterNode("ConnectionRequestURL", "Device.ManagementServer.ConnectionRequestURL");
        connReqUrlNode.setValue(new ParameterValue("http://10.0.0.1:7547/", "xsd:string"));
        connReqUrlNode.setWritable(false);

        ParameterNode paramKeyNode = new ParameterNode("ParameterKey", "Device.ManagementServer.ParameterKey");
        paramKeyNode.setValue(new ParameterValue("", "xsd:string"));
        paramKeyNode.setWritable(true);

        mgmtServerNode.addChild(connReqUrlNode);
        mgmtServerNode.addChild(paramKeyNode);
        deviceNode.addChild(mgmtServerNode);

        ParameterNode wanNode = new ParameterNode("WANDevice", "Device.WANDevice");
        wanNode.setMultiInstance(true);
        deviceNode.addChild(wanNode);

        dataModel.getRoot().addChild(deviceNode);

        when(device.getDataModel()).thenReturn(dataModel);
        when(device.getConfig()).thenReturn(config);
        when(device.getOui()).thenReturn("000000");
        when(device.getProductClass()).thenReturn("SimCPE");
        when(device.getSerialNumber()).thenReturn("SIM00001");
        when(device.getPendingTransfers()).thenReturn(List.of());
    }

    @Test
    void getParameterValuesWithValidParameters() {
        GetParameterValuesHandler handler = new GetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-1");
        request.setMethodName("GetParameterValues");
        request.setRawBody(buildGetParameterValuesXml(
            "Device.DeviceInfo.Manufacturer", "Device.DeviceInfo.SoftwareVersion"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertEquals("GetParameterValuesResponse", response.getMethodName());
        List<SoapEnvelope.ParameterInfo> params = response.getParameterList();
        assertEquals(2, params.size());
        assertEquals("Device.DeviceInfo.Manufacturer", params.get(0).name());
        assertEquals("TestMfg", params.get(0).value());
        assertEquals("Device.DeviceInfo.SoftwareVersion", params.get(1).name());
        assertEquals("1.0.0", params.get(1).value());
    }

    @Test
    void getParameterValuesWithNonExistentParameterReturnsFault9005() {
        GetParameterValuesHandler handler = new GetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-2");
        request.setMethodName("GetParameterValues");
        request.setRawBody(buildGetParameterValuesXml("Device.DeviceInfo.NonExistent"));

        SoapEnvelope response = handler.handle(device, request);

        assertTrue(response.isFault());
        assertEquals(9005, response.getFaultCode());
    }

    @Test
    void setParameterValuesWithWritableParameter() {
        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-3");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.DeviceInfo.ProvisioningCode", "NEW_CODE", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertEquals("0", response.getParameters().get("Status"));
        assertEquals("NEW_CODE", dataModel.getValue("Device.DeviceInfo.ProvisioningCode").getValue());
    }

    @Test
    void setParameterValuesWithReadOnlyParameterReturnsFault9008() {
        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-4");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.DeviceInfo.Manufacturer", "Hacked", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertTrue(response.isFault());
        assertEquals(9008, response.getFaultCode());
    }

    @Test
    void setParameterValuesWithInvalidTypeReturnsFault9007() {
        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-5");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.DeviceInfo.RetryCount", "not_a_number", "xsd:unsignedInt"));

        SoapEnvelope response = handler.handle(device, request);

        assertTrue(response.isFault());
        assertEquals(9007, response.getFaultCode());
    }

    @Test
    void getParameterNamesReturnsCorrectList() {
        GetParameterNamesHandler handler = new GetParameterNamesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-6");
        request.setMethodName("GetParameterNames");
        request.setRawBody(buildGetParameterNamesXml("Device.DeviceInfo.", true));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertNotNull(response.getStringList());
        assertTrue(response.getStringList().contains("Device.DeviceInfo.Manufacturer"));
        assertTrue(response.getStringList().contains("Device.DeviceInfo.SoftwareVersion"));
    }

    @Test
    void addObjectCreatesNewInstance() {
        AddObjectHandler handler = new AddObjectHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-7");
        request.setMethodName("AddObject");
        request.setRawBody(buildAddObjectXml("Device.WANDevice."));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertEquals("1", response.getParameters().get("InstanceNumber"));
        assertEquals("0", response.getParameters().get("Status"));
    }

    @Test
    void deleteObjectRemovesInstance() {
        dataModel.addInstance("Device.WANDevice");
        DeleteObjectHandler handler = new DeleteObjectHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-8");
        request.setMethodName("DeleteObject");
        request.setRawBody(buildDeleteObjectXml("Device.WANDevice.1."));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertEquals("0", response.getParameters().get("Status"));
    }

    @Test
    void getRPCMethodsReturnsCorrectList() {
        RpcRegistry registry = new RpcRegistry(device);
        GetRPCMethodsHandler handler = new GetRPCMethodsHandler(registry);

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("SIM-9");
        request.setMethodName("GetRPCMethods");

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertNotNull(response.getStringList());
        assertTrue(response.getStringList().contains("GetParameterValues"));
        assertTrue(response.getStringList().contains("SetParameterValues"));
        assertTrue(response.getStringList().contains("Reboot"));
        assertTrue(response.getStringList().contains("FactoryReset"));
        assertTrue(response.getStringList().contains("Download"));
    }

    @Test
    void addObjectClonesInstance1AndGetParameterValuesReturnsClonedData() {
        DataModel fullModel = new DataModel();
        ParameterNode deviceNode = new ParameterNode("Device", "Device");

        ParameterNode wanNode = new ParameterNode("WANDevice", "Device.WANDevice");
        wanNode.setMultiInstance(true);
        wanNode.setNextInstanceNumber(2);

        ParameterNode wan1 = new ParameterNode("1", "Device.WANDevice.1");
        ParameterNode wanConn = new ParameterNode("WANConnectionDevice", "Device.WANDevice.1.WANConnectionDevice");
        wanConn.setMultiInstance(true);
        wanConn.setNextInstanceNumber(2);

        ParameterNode wanConn1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1");
        ParameterNode wanIp = new ParameterNode("WANIPConnection", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        wanIp.setMultiInstance(true);
        wanIp.setNextInstanceNumber(2);

        ParameterNode wanIp1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1");
        ParameterNode nameParam = new ParameterNode("Name", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name");
        nameParam.setValue(new ParameterValue("WAN", "xsd:string"));
        nameParam.setWritable(true);
        wanIp1.addChild(nameParam);
        wanIp.addChild(wanIp1);

        wanConn1.addChild(wanIp);
        wanConn.addChild(wanConn1);
        wan1.addChild(wanConn);
        wanNode.addChild(wan1);
        deviceNode.addChild(wanNode);
        fullModel.getRoot().addChild(deviceNode);

        when(device.getDataModel()).thenReturn(fullModel);

        AddObjectHandler addHandler = new AddObjectHandler();
        SoapEnvelope addRequest = new SoapEnvelope();
        addRequest.setCwmpId("SIM-10");
        addRequest.setMethodName("AddObject");
        addRequest.setRawBody(buildAddObjectXml("Device.WANDevice."));

        SoapEnvelope addResponse = addHandler.handle(device, addRequest);
        assertFalse(addResponse.isFault());
        assertEquals("2", addResponse.getParameters().get("InstanceNumber"));

        GetParameterValuesHandler getHandler = new GetParameterValuesHandler();
        SoapEnvelope getRequest = new SoapEnvelope();
        getRequest.setCwmpId("SIM-11");
        getRequest.setMethodName("GetParameterValues");
        getRequest.setRawBody(buildGetParameterValuesXml("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name"));

        SoapEnvelope getResponse = getHandler.handle(device, getRequest);
        assertFalse(getResponse.isFault());
        List<SoapEnvelope.ParameterInfo> params = getResponse.getParameterList();
        assertEquals(1, params.size());
        assertEquals("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name", params.get(0).name());
        assertEquals("WAN", params.get(0).value());
    }

    private String buildGetParameterValuesXml(String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"");
        sb.append(" xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">");
        sb.append("<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID></soap-env:Header>");
        sb.append("<soap-env:Body><cwmp:GetParameterValues>");
        sb.append("<ParameterNames>");
        for (String p : params) {
            sb.append("<string>").append(p).append("</string>");
        }
        sb.append("</ParameterNames>");
        sb.append("</cwmp:GetParameterValues></soap-env:Body></soap-env:Envelope>");
        return sb.toString();
    }

    private String buildSetParameterValuesXml(String name, String value, String type) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\"" +
            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID></soap-env:Header>" +
            "<soap-env:Body><cwmp:SetParameterValues>" +
            "<ParameterList>" +
            "<ParameterValueStruct><Name>" + name + "</Name>" +
            "<Value xsi:type=\"" + type + "\">" + value + "</Value>" +
            "</ParameterValueStruct>" +
            "</ParameterList>" +
            "<ParameterKey></ParameterKey>" +
            "</cwmp:SetParameterValues></soap-env:Body></soap-env:Envelope>";
    }

    private String buildGetParameterNamesXml(String path, boolean nextLevel) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">" +
            "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID></soap-env:Header>" +
            "<soap-env:Body><cwmp:GetParameterNames>" +
            "<ParameterPath>" + path + "</ParameterPath>" +
            "<NextLevel>" + nextLevel + "</NextLevel>" +
            "</cwmp:GetParameterNames></soap-env:Body></soap-env:Envelope>";
    }

    private String buildAddObjectXml(String objectPath) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">" +
            "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID></soap-env:Header>" +
            "<soap-env:Body><cwmp:AddObject>" +
            "<ObjectName>" + objectPath + "</ObjectName>" +
            "<ParameterKey></ParameterKey>" +
            "</cwmp:AddObject></soap-env:Body></soap-env:Envelope>";
    }

    private String buildDeleteObjectXml(String objectPath) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">" +
            "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID></soap-env:Header>" +
            "<soap-env:Body><cwmp:DeleteObject>" +
            "<ObjectName>" + objectPath + "</ObjectName>" +
            "<ParameterKey></ParameterKey>" +
            "</cwmp:DeleteObject></soap-env:Body></soap-env:Envelope>";
    }
}
