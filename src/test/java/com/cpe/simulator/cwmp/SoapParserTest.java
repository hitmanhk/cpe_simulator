package com.cpe.simulator.cwmp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoapParserTest {

    private SoapParser parser;

    @BeforeEach
    void setUp() {
        parser = new SoapParser();
    }

    private String wrapInSoap(String headerContent, String bodyContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:soap-enc=\"http://schemas.xmlsoap.org/soap/encoding/\" " +
            "xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<soap-env:Header>" + headerContent + "</soap-env:Header>" +
            "<soap-env:Body>" + bodyContent + "</soap-env:Body>" +
            "</soap-env:Envelope>";
    }

    @Test
    void parseInformResponseExtractsHoldRequests() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-1</cwmp:ID>",
            "<cwmp:InformResponse><MaxEnvelopes>1</MaxEnvelopes></cwmp:InformResponse>"
        );

        boolean result = parser.parseInformResponse(xml);
        assertTrue(result);
    }

    @Test
    void parseInformResponseReturnsFalseWhenZero() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-2</cwmp:ID>",
            "<cwmp:InformResponse><MaxEnvelopes>0</MaxEnvelopes></cwmp:InformResponse>"
        );

        boolean result = parser.parseInformResponse(xml);
        assertFalse(result);
    }

    @Test
    void parseGetParameterValuesExtractsParameterPaths() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-3</cwmp:ID>",
            "<cwmp:GetParameterValues>" +
            "<ParameterNames soap-enc:arrayType=\"xsd:string[2]\">" +
            "<string>InternetGatewayDevice.DeviceInfo.Manufacturer</string>" +
            "<string>InternetGatewayDevice.DeviceInfo.UpTime</string>" +
            "</ParameterNames>" +
            "</cwmp:GetParameterValues>"
        );

        List<String> params = parser.parseGetParameterValues(xml);
        assertEquals(2, params.size());
        assertEquals("InternetGatewayDevice.DeviceInfo.Manufacturer", params.get(0));
        assertEquals("InternetGatewayDevice.DeviceInfo.UpTime", params.get(1));
    }

    @Test
    void parseGetParameterValuesSingleParameter() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-3b</cwmp:ID>",
            "<cwmp:GetParameterValues>" +
            "<ParameterNames>" +
            "<string>InternetGatewayDevice.DeviceInfo.Manufacturer</string>" +
            "</ParameterNames>" +
            "</cwmp:GetParameterValues>"
        );

        List<String> params = parser.parseGetParameterValues(xml);
        assertEquals(1, params.size());
        assertEquals("InternetGatewayDevice.DeviceInfo.Manufacturer", params.get(0));
    }

    @Test
    void parseSetParameterValuesExtractsParameterValues() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-4</cwmp:ID>",
            "<cwmp:SetParameterValues>" +
            "<ParameterList soap-enc:arrayType=\"cwmp:ParameterValueStruct[2]\">" +
            "<ParameterValueStruct>" +
            "<Name>InternetGatewayDevice.ManagementServer.PeriodicInformInterval</Name>" +
            "<Value xsi:type=\"xsd:unsignedInt\">300</Value>" +
            "</ParameterValueStruct>" +
            "<ParameterValueStruct>" +
            "<Name>InternetGatewayDevice.ManagementServer.URL</Name>" +
            "<Value xsi:type=\"xsd:string\">http://acs.example.com</Value>" +
            "</ParameterValueStruct>" +
            "</ParameterList>" +
            "<ParameterKey>test-key</ParameterKey>" +
            "</cwmp:SetParameterValues>"
        );

        List<SoapEnvelope.ParameterInfo> params = parser.parseSetParameterValues(xml);
        assertEquals(2, params.size());
        assertEquals("InternetGatewayDevice.ManagementServer.PeriodicInformInterval", params.get(0).name());
        assertEquals("300", params.get(0).value());
        assertEquals("InternetGatewayDevice.ManagementServer.URL", params.get(1).name());
        assertEquals("http://acs.example.com", params.get(1).value());
    }

    @Test
    void parseFaultExtractsFaultCodeAndString() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-5</cwmp:ID>",
            "<soap-env:Fault>" +
            "<faultcode>Client</faultcode>" +
            "<faultstring>CWMP fault</faultstring>" +
            "<detail>" +
            "<cwmp:Fault>" +
            "<FaultCode>9005</FaultCode>" +
            "<FaultString>Invalid parameter name</FaultString>" +
            "</cwmp:Fault>" +
            "</detail>" +
            "</soap-env:Fault>"
        );

        SoapParser.FaultInfo fault = parser.parseFault(xml);
        assertEquals(9005, fault.faultCode());
        assertEquals("Invalid parameter name", fault.faultString());
    }

    @Test
    void extractMethodName() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-6</cwmp:ID>",
            "<cwmp:GetParameterValues><ParameterNames><string>test</string></ParameterNames></cwmp:GetParameterValues>"
        );

        String methodName = parser.extractMethodName(xml);
        assertEquals("GetParameterValues", methodName);
    }

    @Test
    void extractMethodNameForInformResponse() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-7</cwmp:ID>",
            "<cwmp:InformResponse><MaxEnvelopes>1</MaxEnvelopes></cwmp:InformResponse>"
        );

        String methodName = parser.extractMethodName(xml);
        assertEquals("InformResponse", methodName);
    }

    @Test
    void extractCwmpId() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-TEST-99</cwmp:ID>",
            "<cwmp:InformResponse><MaxEnvelopes>1</MaxEnvelopes></cwmp:InformResponse>"
        );

        String cwmpId = parser.extractCwmpId(xml);
        assertEquals("ACS-TEST-99", cwmpId);
    }

    @Test
    void parseFullEnvelope() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-8</cwmp:ID>",
            "<cwmp:Reboot><CommandKey>reboot-key-1</CommandKey></cwmp:Reboot>"
        );

        SoapEnvelope envelope = parser.parse(xml);
        assertEquals("ACS-8", envelope.getCwmpId());
        assertEquals("Reboot", envelope.getMethodName());
        assertFalse(envelope.isFault());
    }

    @Test
    void parseFaultEnvelope() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-9</cwmp:ID>",
            "<soap-env:Fault>" +
            "<faultcode>Client</faultcode>" +
            "<faultstring>CWMP fault</faultstring>" +
            "<detail>" +
            "<cwmp:Fault>" +
            "<FaultCode>8000</FaultCode>" +
            "<FaultString>Method not supported</FaultString>" +
            "</cwmp:Fault>" +
            "</detail>" +
            "</soap-env:Fault>"
        );

        SoapEnvelope envelope = parser.parse(xml);
        assertTrue(envelope.isFault());
        assertEquals(8000, envelope.getFaultCode());
        assertEquals("Method not supported", envelope.getFaultString());
    }

    @Test
    void parseDownload() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-10</cwmp:ID>",
            "<cwmp:Download>" +
            "<CommandKey>dl-key-1</CommandKey>" +
            "<FileType>1 Firmware Upgrade Image</FileType>" +
            "<URL>http://firmware.example.com/v2.0.bin</URL>" +
            "<FileSize>10485760</FileSize>" +
            "<TargetFileName>firmware.bin</TargetFileName>" +
            "</cwmp:Download>"
        );

        SoapParser.DownloadRequest req = parser.parseDownload(xml);
        assertEquals("dl-key-1", req.commandKey());
        assertEquals("1 Firmware Upgrade Image", req.fileType());
        assertEquals("http://firmware.example.com/v2.0.bin", req.url());
        assertEquals(10485760, req.fileSize());
        assertEquals("firmware.bin", req.targetFileName());
    }

    @Test
    void parseReboot() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-11</cwmp:ID>",
            "<cwmp:Reboot><CommandKey>reboot-key-1</CommandKey></cwmp:Reboot>"
        );

        SoapParser.RebootRequest req = parser.parseReboot(xml);
        assertEquals("reboot-key-1", req.commandKey());
    }

    @Test
    void parseGetParameterNames() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-12</cwmp:ID>",
            "<cwmp:GetParameterNames>" +
            "<ParameterPath>InternetGatewayDevice.DeviceInfo.</ParameterPath>" +
            "<NextLevel>true</NextLevel>" +
            "</cwmp:GetParameterNames>"
        );

        SoapParser.ParameterNamesRequest req = parser.parseGetParameterNames(xml);
        assertEquals("InternetGatewayDevice.DeviceInfo.", req.path());
        assertTrue(req.nextLevel());
    }

    @Test
    void parseScheduleInform() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-13</cwmp:ID>",
            "<cwmp:ScheduleInform>" +
            "<CommandKey>sched-key-1</CommandKey>" +
            "<DelaySeconds>30</DelaySeconds>" +
            "</cwmp:ScheduleInform>"
        );

        SoapParser.ScheduleInformRequest req = parser.parseScheduleInform(xml);
        assertEquals("sched-key-1", req.commandKey());
        assertEquals(30, req.delaySeconds());
    }

    @Test
    void parseAddObject() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-14</cwmp:ID>",
            "<cwmp:AddObject>" +
            "<ObjectName>InternetGatewayDevice.LANDevice.</ObjectName>" +
            "<ParameterKey>add-key-1</ParameterKey>" +
            "</cwmp:AddObject>"
        );

        SoapParser.AddObjectRequest req = parser.parseAddObject(xml);
        assertEquals("InternetGatewayDevice.LANDevice.", req.objectPath());
        assertEquals("add-key-1", req.parameterKey());
    }

    @Test
    void parseDeleteObject() throws Exception {
        String xml = wrapInSoap(
            "<cwmp:ID soap-env:mustUnderstand=\"1\">ACS-15</cwmp:ID>",
            "<cwmp:DeleteObject>" +
            "<ObjectName>InternetGatewayDevice.LANDevice.1.</ObjectName>" +
            "<ParameterKey>del-key-1</ParameterKey>" +
            "</cwmp:DeleteObject>"
        );

        SoapParser.DeleteObjectRequest req = parser.parseDeleteObject(xml);
        assertEquals("InternetGatewayDevice.LANDevice.1.", req.objectPath());
        assertEquals("del-key-1", req.parameterKey());
    }
}
