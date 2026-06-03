package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoapBuilderTest {

    private SoapBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SoapBuilder();
    }

    @Test
    void buildInformProducesValidXmlWithCorrectStructure() {
        SoapEnvelope.DeviceIdStruct deviceId = new SoapEnvelope.DeviceIdStruct("TestMfg", "001122", "Router", "SN12345");
        List<SoapEnvelope.EventStruct> events = List.of(
            new SoapEnvelope.EventStruct("0 BOOTSTRAP", ""),
            new SoapEnvelope.EventStruct("1 BOOT", "")
        );
        List<SoapEnvelope.ParameterInfo> params = List.of(
            new SoapEnvelope.ParameterInfo("InternetGatewayDevice.DeviceInfo.Manufacturer", "TestMfg", "xsd:string"),
            new SoapEnvelope.ParameterInfo("InternetGatewayDevice.DeviceInfo.UpTime", "12345", "xsd:unsignedInt")
        );

        String xml = builder.buildInform(deviceId, events, params, "SIM-1");

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("soap-env:Envelope"));
        assertTrue(xml.contains("xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\""));
        assertTrue(xml.contains("xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\""));
        assertTrue(xml.contains("<cwmp:ID soap-env:mustUnderstand=\"1\">SIM-1</cwmp:ID>"));
        assertTrue(xml.contains("<cwmp:Inform>"));
        assertTrue(xml.contains("<Manufacturer>TestMfg</Manufacturer>"));
        assertTrue(xml.contains("<OUI>001122</OUI>"));
        assertTrue(xml.contains("<ProductClass>Router</ProductClass>"));
        assertTrue(xml.contains("<SerialNumber>SN12345</SerialNumber>"));
        assertTrue(xml.contains("soap-enc:arrayType=\"cwmp:EventStruct[2]\""));
        assertTrue(xml.contains("<EventCode>0 BOOTSTRAP</EventCode>"));
        assertTrue(xml.contains("<EventCode>1 BOOT</EventCode>"));
        assertTrue(xml.contains("<MaxEnvelopes>1</MaxEnvelopes>"));
        assertTrue(xml.contains("<RetryCount>0</RetryCount>"));
        assertTrue(xml.contains("soap-enc:arrayType=\"cwmp:ParameterValueStruct[2]\""));
        assertTrue(xml.contains("<Name>InternetGatewayDevice.DeviceInfo.Manufacturer</Name>"));
        assertTrue(xml.contains("xsi:type=\"xsd:string\">TestMfg</Value>"));
        assertTrue(xml.contains("<Name>InternetGatewayDevice.DeviceInfo.UpTime</Name>"));
        assertTrue(xml.contains("xsi:type=\"xsd:unsignedInt\">12345</Value>"));
        assertTrue(xml.contains("</cwmp:Inform>"));
        assertTrue(xml.contains("</soap-env:Envelope>"));
    }

    @Test
    void buildInformEscapesXmlSpecialCharacters() {
        SoapEnvelope.DeviceIdStruct deviceId = new SoapEnvelope.DeviceIdStruct("Mfg & Co", "001122", "Router<Pro>", "SN\"123");
        List<SoapEnvelope.EventStruct> events = List.of(new SoapEnvelope.EventStruct("1 BOOT", ""));
        List<SoapEnvelope.ParameterInfo> params = List.of();

        String xml = builder.buildInform(deviceId, events, params, "SIM-2");

        assertTrue(xml.contains("Mfg &amp; Co"));
        assertTrue(xml.contains("Router&lt;Pro&gt;"));
        assertTrue(xml.contains("SN&quot;123"));
    }

    @Test
    void buildGetParameterValuesResponseWithMultipleParameters() {
        List<SoapEnvelope.ParameterInfo> params = List.of(
            new SoapEnvelope.ParameterInfo("Device.DeviceInfo.Manufacturer", "TestMfg", "xsd:string"),
            new SoapEnvelope.ParameterInfo("Device.DeviceInfo.UpTime", "9999", "xsd:unsignedInt"),
            new SoapEnvelope.ParameterInfo("Device.DeviceInfo.SoftwareVersion", "1.0.0", "xsd:string")
        );

        String xml = builder.buildGetParameterValuesResponse(params, "SIM-3");

        assertTrue(xml.contains("<cwmp:GetParameterValuesResponse>"));
        assertTrue(xml.contains("soap-enc:arrayType=\"cwmp:ParameterValueStruct[3]\""));
        assertTrue(xml.contains("<Name>Device.DeviceInfo.Manufacturer</Name>"));
        assertTrue(xml.contains("<Name>Device.DeviceInfo.UpTime</Name>"));
        assertTrue(xml.contains("<Name>Device.DeviceInfo.SoftwareVersion</Name>"));
        assertTrue(xml.contains("</cwmp:GetParameterValuesResponse>"));
    }

    @Test
    void buildSetParameterValuesResponse() {
        String xml = builder.buildSetParameterValuesResponse(0, "SIM-4");

        assertTrue(xml.contains("<cwmp:SetParameterValuesResponse>"));
        assertTrue(xml.contains("<Status>0</Status>"));
        assertTrue(xml.contains("</cwmp:SetParameterValuesResponse>"));
    }

    @Test
    void buildFault() {
        String xml = builder.buildFault(9005, "Invalid parameter name", "SIM-5");

        assertTrue(xml.contains("<soap-env:Fault>"));
        assertTrue(xml.contains("<faultcode>Client</faultcode>"));
        assertTrue(xml.contains("<faultstring>CWMP fault</faultstring>"));
        assertTrue(xml.contains("<cwmp:Fault>"));
        assertTrue(xml.contains("<FaultCode>9005</FaultCode>"));
        assertTrue(xml.contains("<FaultString>Invalid parameter name</FaultString>"));
        assertTrue(xml.contains("</cwmp:Fault>"));
        assertTrue(xml.contains("</soap-env:Fault>"));
    }

    @Test
    void buildEmptyRequest() {
        String xml = builder.buildEmptyRequest("SIM-6");

        assertTrue(xml.contains("<cwmp:ID soap-env:mustUnderstand=\"1\">SIM-6</cwmp:ID>"));
        assertTrue(xml.contains("<soap-env:Body>"));
        assertTrue(xml.contains("</soap-env:Body>"));
        assertFalse(xml.contains("<cwmp:Inform"));
        assertFalse(xml.contains("<cwmp:GetParameterValues"));
    }

    @Test
    void buildDownloadResponse() {
        String xml = builder.buildDownloadResponse(1, "2024-01-01T00:00:00Z", "2024-01-01T00:05:00Z", "SIM-7");

        assertTrue(xml.contains("<cwmp:DownloadResponse>"));
        assertTrue(xml.contains("<Status>1</Status>"));
        assertTrue(xml.contains("<StartTime>2024-01-01T00:00:00Z</StartTime>"));
        assertTrue(xml.contains("<CompleteTime>2024-01-01T00:05:00Z</CompleteTime>"));
        assertTrue(xml.contains("</cwmp:DownloadResponse>"));
    }

    @Test
    void buildRebootResponse() {
        String xml = builder.buildRebootResponse("SIM-8");

        assertTrue(xml.contains("<cwmp:RebootResponse>"));
        assertTrue(xml.contains("</cwmp:RebootResponse>"));
        assertTrue(xml.contains("<cwmp:ID soap-env:mustUnderstand=\"1\">SIM-8</cwmp:ID>"));
    }

    @Test
    void buildTransferComplete() {
        String xml = builder.buildTransferComplete("cmd-key-1", 0, "2024-01-01T00:00:00Z", "2024-01-01T00:01:00Z", "SIM-9");

        assertTrue(xml.contains("<cwmp:TransferComplete>"));
        assertTrue(xml.contains("<CommandKey>cmd-key-1</CommandKey>"));
        assertTrue(xml.contains("<FaultCode>0</FaultCode>"));
        assertTrue(xml.contains("<StartTime>2024-01-01T00:00:00Z</StartTime>"));
        assertTrue(xml.contains("<CompleteTime>2024-01-01T00:01:00Z</CompleteTime>"));
    }

    @Test
    void buildGetRPCMethodsResponse() {
        List<String> methods = List.of("GetParameterValues", "SetParameterValues", "Reboot");
        String xml = builder.buildGetRPCMethodsResponse(methods, "SIM-10");

        assertTrue(xml.contains("<cwmp:GetRPCMethodsResponse>"));
        assertTrue(xml.contains("soap-enc:arrayType=\"xsd:string[3]\""));
        assertTrue(xml.contains("<string>GetParameterValues</string>"));
        assertTrue(xml.contains("<string>SetParameterValues</string>"));
        assertTrue(xml.contains("<string>Reboot</string>"));
    }

    @Test
    void buildFactoryResetResponse() {
        String xml = builder.buildFactoryResetResponse("SIM-11");

        assertTrue(xml.contains("<cwmp:FactoryResetResponse>"));
        assertTrue(xml.contains("</cwmp:FactoryResetResponse>"));
    }

    @Test
    void buildAddObjectResponse() {
        String xml = builder.buildAddObjectResponse(5, 0, "SIM-12");

        assertTrue(xml.contains("<cwmp:AddObjectResponse>"));
        assertTrue(xml.contains("<InstanceNumber>5</InstanceNumber>"));
        assertTrue(xml.contains("<Status>0</Status>"));
    }

    @Test
    void buildDeleteObjectResponse() {
        String xml = builder.buildDeleteObjectResponse(0, "SIM-13");

        assertTrue(xml.contains("<cwmp:DeleteObjectResponse>"));
        assertTrue(xml.contains("<Status>0</Status>"));
    }
}
