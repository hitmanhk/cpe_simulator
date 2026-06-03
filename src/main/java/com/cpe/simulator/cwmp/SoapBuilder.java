package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.XmlUtil;

import java.time.Instant;
import java.util.List;

public class SoapBuilder {

    public record ParameterAttribute(String name, int notification, List<String> accessList) {}

    public record QueuedTransfer(String commandKey, String fileType, int state, int fileSize) {}

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private StringBuilder envelopeStart(String cwmpId) {
        StringBuilder sb = new StringBuilder(XML_DECL);
        sb.append("<soap-env:Envelope");
        sb.append(" xmlns:soap-env=\"").append(XmlUtil.NS_SOAP_ENV).append("\"");
        sb.append(" xmlns:soap-enc=\"").append(XmlUtil.NS_SOAP_ENC).append("\"");
        sb.append(" xmlns:cwmp=\"").append(XmlUtil.NS_CWMP).append("\"");
        sb.append(" xmlns:xsd=\"").append(XmlUtil.NS_XSD).append("\"");
        sb.append(" xmlns:xsi=\"").append(XmlUtil.NS_XSI).append("\"");
        sb.append(">\n");
        sb.append("<soap-env:Header>\n");
        sb.append("<cwmp:ID soap-env:mustUnderstand=\"1\">").append(XmlUtil.escapeXml(cwmpId)).append("</cwmp:ID>\n");
        sb.append("</soap-env:Header>\n");
        sb.append("<soap-env:Body>\n");
        return sb;
    }

    private String envelopeEnd(StringBuilder sb) {
        sb.append("</soap-env:Body>\n");
        sb.append("</soap-env:Envelope>");
        return sb.toString();
    }

    public String buildInform(SoapEnvelope.DeviceIdStruct deviceId,
                              List<SoapEnvelope.EventStruct> events,
                              List<SoapEnvelope.ParameterInfo> params,
                              String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:Inform>\n");
        sb.append("<DeviceId>\n");
        sb.append("<Manufacturer>").append(XmlUtil.escapeXml(deviceId.manufacturer())).append("</Manufacturer>\n");
        sb.append("<OUI>").append(XmlUtil.escapeXml(deviceId.oui())).append("</OUI>\n");
        sb.append("<ProductClass>").append(XmlUtil.escapeXml(deviceId.productClass())).append("</ProductClass>\n");
        sb.append("<SerialNumber>").append(XmlUtil.escapeXml(deviceId.serialNumber())).append("</SerialNumber>\n");
        sb.append("</DeviceId>\n");
        sb.append("<Event soap-enc:arrayType=\"cwmp:EventStruct[").append(events.size()).append("]\">\n");
        for (SoapEnvelope.EventStruct event : events) {
            sb.append("<EventStruct>\n");
            sb.append("<EventCode>").append(XmlUtil.escapeXml(event.eventCode())).append("</EventCode>\n");
            sb.append("<CommandKey>").append(XmlUtil.escapeXml(event.commandKey())).append("</CommandKey>\n");
            sb.append("</EventStruct>\n");
        }
        sb.append("</Event>\n");
        sb.append("<MaxEnvelopes>1</MaxEnvelopes>\n");
        sb.append("<CurrentTime>").append(Instant.now().toString()).append("</CurrentTime>\n");
        sb.append("<RetryCount>0</RetryCount>\n");
        sb.append("<ParameterList soap-enc:arrayType=\"cwmp:ParameterValueStruct[").append(params.size()).append("]\">\n");
        for (SoapEnvelope.ParameterInfo p : params) {
            sb.append("<ParameterValueStruct>\n");
            sb.append("<Name>").append(XmlUtil.escapeXml(p.name())).append("</Name>\n");
            sb.append("<Value xsi:type=\"").append(XmlUtil.escapeXml(p.type())).append("\">").append(XmlUtil.escapeXml(p.value())).append("</Value>\n");
            sb.append("</ParameterValueStruct>\n");
        }
        sb.append("</ParameterList>\n");
        sb.append("</cwmp:Inform>\n");
        return envelopeEnd(sb);
    }

    public String buildTransferComplete(String commandKey, int faultCode, String startTime, String completeTime, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:TransferComplete>\n");
        sb.append("<CommandKey>").append(XmlUtil.escapeXml(commandKey)).append("</CommandKey>\n");
        sb.append("<FaultCode>").append(faultCode).append("</FaultCode>\n");
        sb.append("<StartTime>").append(XmlUtil.escapeXml(startTime)).append("</StartTime>\n");
        sb.append("<CompleteTime>").append(XmlUtil.escapeXml(completeTime)).append("</CompleteTime>\n");
        sb.append("</cwmp:TransferComplete>\n");
        return envelopeEnd(sb);
    }

    public String buildGetRPCMethodsResponse(List<String> methods, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetRPCMethodsResponse>\n");
        sb.append("<MethodList soap-enc:arrayType=\"xsd:string[").append(methods.size()).append("]\">\n");
        for (String method : methods) {
            sb.append("<string>").append(XmlUtil.escapeXml(method)).append("</string>\n");
        }
        sb.append("</MethodList>\n");
        sb.append("</cwmp:GetRPCMethodsResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildEmptyRequest(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        return envelopeEnd(sb);
    }

    public String buildGetParameterValuesResponse(List<SoapEnvelope.ParameterInfo> params, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetParameterValuesResponse>\n");
        sb.append("<ParameterList soap-enc:arrayType=\"cwmp:ParameterValueStruct[").append(params.size()).append("]\">\n");
        for (SoapEnvelope.ParameterInfo p : params) {
            sb.append("<ParameterValueStruct>\n");
            sb.append("<Name>").append(XmlUtil.escapeXml(p.name())).append("</Name>\n");
            sb.append("<Value xsi:type=\"").append(XmlUtil.escapeXml(p.type())).append("\">").append(XmlUtil.escapeXml(p.value())).append("</Value>\n");
            sb.append("</ParameterValueStruct>\n");
        }
        sb.append("</ParameterList>\n");
        sb.append("</cwmp:GetParameterValuesResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildSetParameterValuesResponse(int status, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:SetParameterValuesResponse>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("</cwmp:SetParameterValuesResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildGetParameterNamesResponse(List<String> paramNames, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetParameterNamesResponse>\n");
        sb.append("<ParameterList soap-enc:arrayType=\"cwmp:ParameterInfoStruct[").append(paramNames.size()).append("]\">\n");
        for (String name : paramNames) {
            sb.append("<ParameterInfoStruct>\n");
            sb.append("<Name>").append(XmlUtil.escapeXml(name)).append("</Name>\n");
            sb.append("<Writable>true</Writable>\n");
            sb.append("</ParameterInfoStruct>\n");
        }
        sb.append("</ParameterList>\n");
        sb.append("</cwmp:GetParameterNamesResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildGetParameterAttributesResponse(List<ParameterAttribute> attrs, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetParameterAttributesResponse>\n");
        sb.append("<ParameterList soap-enc:arrayType=\"cwmp:ParameterAttributeStruct[").append(attrs.size()).append("]\">\n");
        for (ParameterAttribute attr : attrs) {
            sb.append("<ParameterAttributeStruct>\n");
            sb.append("<Name>").append(XmlUtil.escapeXml(attr.name())).append("</Name>\n");
            sb.append("<Notification>").append(attr.notification()).append("</Notification>\n");
            sb.append("<AccessList soap-enc:arrayType=\"xsd:string[").append(attr.accessList().size()).append("]\">\n");
            for (String access : attr.accessList()) {
                sb.append("<string>").append(XmlUtil.escapeXml(access)).append("</string>\n");
            }
            sb.append("</AccessList>\n");
            sb.append("</ParameterAttributeStruct>\n");
        }
        sb.append("</ParameterList>\n");
        sb.append("</cwmp:GetParameterAttributesResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildSetParameterAttributesResponse(int status, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:SetParameterAttributesResponse>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("</cwmp:SetParameterAttributesResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildAddObjectResponse(int instanceNumber, int status, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:AddObjectResponse>\n");
        sb.append("<InstanceNumber>").append(instanceNumber).append("</InstanceNumber>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("</cwmp:AddObjectResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildDeleteObjectResponse(int status, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:DeleteObjectResponse>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("</cwmp:DeleteObjectResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildDownloadResponse(int status, String startTime, String completeTime, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:DownloadResponse>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("<StartTime>").append(XmlUtil.escapeXml(startTime)).append("</StartTime>\n");
        sb.append("<CompleteTime>").append(XmlUtil.escapeXml(completeTime)).append("</CompleteTime>\n");
        sb.append("</cwmp:DownloadResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildRebootResponse(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:RebootResponse>\n");
        sb.append("</cwmp:RebootResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildFactoryResetResponse(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:FactoryResetResponse>\n");
        sb.append("</cwmp:FactoryResetResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildScheduleInformResponse(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:ScheduleInformResponse>\n");
        sb.append("</cwmp:ScheduleInformResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildGetQueuedTransfersResponse(List<QueuedTransfer> transfers, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetQueuedTransfersResponse>\n");
        sb.append("<TransferList soap-enc:arrayType=\"cwmp:QueuedTransferStruct[").append(transfers.size()).append("]\">\n");
        for (QueuedTransfer t : transfers) {
            sb.append("<QueuedTransferStruct>\n");
            sb.append("<CommandKey>").append(XmlUtil.escapeXml(t.commandKey())).append("</CommandKey>\n");
            sb.append("<State>").append(t.state()).append("</State>\n");
            sb.append("</QueuedTransferStruct>\n");
        }
        sb.append("</TransferList>\n");
        sb.append("</cwmp:GetQueuedTransfersResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildGetAllQueuedTransfersResponse(List<QueuedTransfer> transfers, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetAllQueuedTransfersResponse>\n");
        sb.append("<TransferList soap-enc:arrayType=\"cwmp:AllQueuedTransferStruct[").append(transfers.size()).append("]\">\n");
        for (QueuedTransfer t : transfers) {
            sb.append("<AllQueuedTransferStruct>\n");
            sb.append("<CommandKey>").append(XmlUtil.escapeXml(t.commandKey())).append("</CommandKey>\n");
            sb.append("<State>").append(t.state()).append("</State>\n");
            sb.append("<FileSize>").append(t.fileSize()).append("</FileSize>\n");
            sb.append("<FileType>").append(XmlUtil.escapeXml(t.fileType())).append("</FileType>\n");
            sb.append("</AllQueuedTransferStruct>\n");
        }
        sb.append("</TransferList>\n");
        sb.append("</cwmp:GetAllQueuedTransfersResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildGetOptionsResponse(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:GetOptionsResponse>\n");
        sb.append("<OptionList soap-enc:arrayType=\"cwmp:OptionStruct[0]\">\n");
        sb.append("</OptionList>\n");
        sb.append("</cwmp:GetOptionsResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildSetVouchersResponse(String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:SetVouchersResponse>\n");
        sb.append("</cwmp:SetVouchersResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildUploadResponse(int status, String startTime, String completeTime, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<cwmp:UploadResponse>\n");
        sb.append("<Status>").append(status).append("</Status>\n");
        sb.append("<StartTime>").append(XmlUtil.escapeXml(startTime)).append("</StartTime>\n");
        sb.append("<CompleteTime>").append(XmlUtil.escapeXml(completeTime)).append("</CompleteTime>\n");
        sb.append("</cwmp:UploadResponse>\n");
        return envelopeEnd(sb);
    }

    public String buildFault(int faultCode, String faultString, String cwmpId) {
        StringBuilder sb = envelopeStart(cwmpId);
        sb.append("<soap-env:Fault>\n");
        sb.append("<faultcode>Client</faultcode>\n");
        sb.append("<faultstring>CWMP fault</faultstring>\n");
        sb.append("<detail>\n");
        sb.append("<cwmp:Fault>\n");
        sb.append("<FaultCode>").append(faultCode).append("</FaultCode>\n");
        sb.append("<FaultString>").append(XmlUtil.escapeXml(faultString)).append("</FaultString>\n");
        sb.append("</cwmp:Fault>\n");
        sb.append("</detail>\n");
        sb.append("</soap-env:Fault>\n");
        return envelopeEnd(sb);
    }
}
