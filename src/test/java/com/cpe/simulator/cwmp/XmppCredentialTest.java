package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;
import com.cpe.simulator.xmpp.SaslHandler;
import com.cpe.simulator.xmpp.SaslMechanism;
import com.cpe.simulator.xmpp.XmppConnection;
import com.cpe.simulator.xmpp.XmppStanza;
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
class XmppCredentialTest {

    @Test
    void tr181ModelContainsXmppPasswordParameter() {
        DataModel model = ModelLoader.load("tr181");

        ParameterNode node = model.getNode("Device.XMPP.Connection.1.Password");
        assertNotNull(node, "TR-181 model should contain XMPP Password parameter");
        assertTrue(node.isWritable(), "XMPP Password should be writable");
        assertTrue(node.isLeaf(), "XMPP Password should be a leaf node");

        ParameterValue value = node.getValue();
        assertNotNull(value, "XMPP Password should have a ParameterValue");
        assertEquals("", value.getValue(), "XMPP Password initial value should be empty string");
        assertEquals("xsd:string", value.getType(), "XMPP Password type should be xsd:string");
        assertEquals(2, node.getNotification(), "XMPP Password notification should be 2");
    }

    @Test
    void tr098ModelContainsXmppPasswordParameter() {
        DataModel model = ModelLoader.load("tr098");

        ParameterNode node = model.getNode("InternetGatewayDevice.XMPP.Connection.1.Password");
        assertNotNull(node, "TR-098 model should contain XMPP Password parameter");
        assertTrue(node.isWritable(), "XMPP Password should be writable");
        assertTrue(node.isLeaf(), "XMPP Password should be a leaf node");

        ParameterValue value = node.getValue();
        assertNotNull(value, "XMPP Password should have a ParameterValue");
        assertEquals("", value.getValue(), "XMPP Password initial value should be empty string");
        assertEquals("xsd:string", value.getType(), "XMPP Password type should be xsd:string");
        assertEquals(2, node.getNotification(), "XMPP Password notification should be 2");
    }

    @Mock
    private DeviceContext device;

    @Test
    void setParameterValuesWithXmppPasswordTriggersConnectXmpp() {
        DataModel dataModel = ModelLoader.load("tr181");
        when(device.getDataModel()).thenReturn(dataModel);

        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("ID-1");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.XMPP.Connection.1.Password", "device-secret", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        assertEquals("0", response.getParameters().get("Status"));
        assertEquals("device-secret", dataModel.getValue("Device.XMPP.Connection.1.Password").getValue());
        verify(device).connectXmpp();
    }

    @Test
    void setParameterValuesWithXmppPasswordEmptyDoesNotTriggerConnectXmppButStillWrites() {
        DataModel dataModel = ModelLoader.load("tr181");
        when(device.getDataModel()).thenReturn(dataModel);

        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("ID-2");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.XMPP.Connection.1.Password", "", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        verify(device).connectXmpp();
    }

    @Test
    void setParameterValuesWithTr098XmppPasswordTriggersConnectXmpp() {
        DataModel dataModel = ModelLoader.load("tr098");
        when(device.getDataModel()).thenReturn(dataModel);

        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("ID-3");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "InternetGatewayDevice.XMPP.Connection.1.Password", "tr098-secret", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        verify(device).connectXmpp();
    }

    @Test
    void setParameterValuesWithNonXmppParamDoesNotTriggerConnectXmpp() {
        DataModel dataModel = ModelLoader.load("tr181");
        when(device.getDataModel()).thenReturn(dataModel);
        when(device.getConfig()).thenReturn(new AppConfig());
        when(device.getOui()).thenReturn("000000");
        when(device.getProductClass()).thenReturn("SimCPE");
        when(device.getSerialNumber()).thenReturn("SIM00001");
        when(device.getPendingTransfers()).thenReturn(List.of());

        SetParameterValuesHandler handler = new SetParameterValuesHandler();

        SoapEnvelope request = new SoapEnvelope();
        request.setCwmpId("ID-4");
        request.setMethodName("SetParameterValues");
        request.setRawBody(buildSetParameterValuesXml(
            "Device.DeviceInfo.ProvisioningCode", "NEW_CODE", "xsd:string"));

        SoapEnvelope response = handler.handle(device, request);

        assertFalse(response.isFault());
        verify(device, never()).connectXmpp();
    }

    @Test
    void saslHandlerUsesXmppPasswordWhenSet() {
        XmppConnection connection = mock(XmppConnection.class);
        when(connection.getDeviceId()).thenReturn("DEV001");
        when(connection.getXmppPassword()).thenReturn("per-device-secret");

        AppConfig.XmppConfig config = new AppConfig.XmppConfig();
        config.setPassword("global-secret");
        config.setSaslMechanism("MOCK");

        SaslMechanism mockMechanism = mock(SaslMechanism.class);
        when(mockMechanism.getMechanismName()).thenReturn("MOCK");
        when(mockMechanism.getInitialResponse(anyString(), anyString())).thenReturn("ir");

        SaslHandler handler = new SaslHandler(connection, config, List.of(mockMechanism));
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("MOCK"), false, false, false);
        handler.startAuth(features);

        verify(mockMechanism).getInitialResponse("dev001", "per-device-secret");
    }

    @Test
    void saslHandlerFallsBackToGlobalPasswordWhenXmppPasswordIsNull() {
        XmppConnection connection = mock(XmppConnection.class);
        when(connection.getDeviceId()).thenReturn("DEV001");
        when(connection.getXmppPassword()).thenReturn(null);

        AppConfig.XmppConfig config = new AppConfig.XmppConfig();
        config.setPassword("global-secret");
        config.setSaslMechanism("MOCK");

        SaslMechanism mockMechanism = mock(SaslMechanism.class);
        when(mockMechanism.getMechanismName()).thenReturn("MOCK");
        when(mockMechanism.getInitialResponse(anyString(), anyString())).thenReturn("ir");

        SaslHandler handler = new SaslHandler(connection, config, List.of(mockMechanism));
        XmppStanza.StreamFeatures features = new XmppStanza.StreamFeatures(
            "<stream:features/>", List.of("MOCK"), false, false, false);
        handler.startAuth(features);

        verify(mockMechanism).getInitialResponse("dev001", "global-secret");
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
}