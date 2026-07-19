package com.cpe.acsit.acs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds ACS→CPE CWMP SOAP messages (InformResponse and every RPC request),
 * mirroring the wire format the simulator's SoapParser expects.
 */
public final class CwmpRpc {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private CwmpRpc() {
    }

    public static String nextId() {
        return "acs-it-" + ID_COUNTER.incrementAndGet();
    }

    private static StringBuilder envelope(String cwmpId) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<soap-env:Envelope")
          .append(" xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"")
          .append(" xmlns:soap-enc=\"http://schemas.xmlsoap.org/soap/encoding/\"")
          .append(" xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\"")
          .append(" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"")
          .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
          .append("<soap-env:Header>\n<cwmp:ID soap-env:mustUnderstand=\"1\">")
          .append(escape(cwmpId)).append("</cwmp:ID>\n</soap-env:Header>\n<soap-env:Body>\n");
        return sb;
    }

    private static String end(StringBuilder sb) {
        sb.append("</soap-env:Body>\n</soap-env:Envelope>");
        return sb.toString();
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static String informResponse(String cwmpId, int maxEnvelopes) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:InformResponse>\n<MaxEnvelopes>").append(maxEnvelopes)
          .append("</MaxEnvelopes>\n</cwmp:InformResponse>\n");
        return end(sb);
    }

    public static String getParameterValues(String cwmpId, String... paths) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:GetParameterValues>\n<ParameterNames soap-enc:arrayType=\"xsd:string[")
          .append(paths.length).append("]\">\n");
        for (String path : paths) {
            sb.append("<string>").append(escape(path)).append("</string>\n");
        }
        sb.append("</ParameterNames>\n</cwmp:GetParameterValues>\n");
        return end(sb);
    }

    /** params: name → value; types default to xsd:string when null. */
    public static String setParameterValues(String cwmpId, Map<String, String> params, String parameterKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:SetParameterValues>\n<ParameterList soap-enc:arrayType=\"cwmp:ParameterValueStruct[")
          .append(params.size()).append("]\">\n");
        params.forEach((name, value) -> sb.append("<ParameterValueStruct>\n<Name>").append(escape(name))
                .append("</Name>\n<Value xsi:type=\"xsd:string\">").append(escape(value))
                .append("</Value>\n</ParameterValueStruct>\n"));
        sb.append("</ParameterList>\n<ParameterKey>").append(escape(parameterKey))
          .append("</ParameterKey>\n</cwmp:SetParameterValues>\n");
        return end(sb);
    }

    /** Typed variant: entries of {name, value, xsiType}. */
    public static String setParameterValuesTyped(String cwmpId, List<String[]> entries, String parameterKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:SetParameterValues>\n<ParameterList soap-enc:arrayType=\"cwmp:ParameterValueStruct[")
          .append(entries.size()).append("]\">\n");
        for (String[] entry : entries) {
            sb.append("<ParameterValueStruct>\n<Name>").append(escape(entry[0]))
              .append("</Name>\n<Value xsi:type=\"").append(entry[2]).append("\">")
              .append(escape(entry[1])).append("</Value>\n</ParameterValueStruct>\n");
        }
        sb.append("</ParameterList>\n<ParameterKey>").append(escape(parameterKey))
          .append("</ParameterKey>\n</cwmp:SetParameterValues>\n");
        return end(sb);
    }

    public static String getParameterNames(String cwmpId, String path, boolean nextLevel) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:GetParameterNames>\n<ParameterPath>").append(escape(path))
          .append("</ParameterPath>\n<NextLevel>").append(nextLevel)
          .append("</NextLevel>\n</cwmp:GetParameterNames>\n");
        return end(sb);
    }

    public static String getParameterAttributes(String cwmpId, String... paths) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:GetParameterAttributes>\n<ParameterNames soap-enc:arrayType=\"xsd:string[")
          .append(paths.length).append("]\">\n");
        for (String path : paths) {
            sb.append("<string>").append(escape(path)).append("</string>\n");
        }
        sb.append("</ParameterNames>\n</cwmp:GetParameterAttributes>\n");
        return end(sb);
    }

    public static String setParameterAttributes(String cwmpId, String name, boolean notificationChange,
                                                int notification, boolean accessListChange,
                                                String... accessList) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:SetParameterAttributes>\n<ParameterList soap-enc:arrayType=\"cwmp:SetParameterAttributesStruct[1]\">\n")
          .append("<SetParameterAttributesStruct>\n<Name>").append(escape(name)).append("</Name>\n")
          .append("<NotificationChange>").append(notificationChange).append("</NotificationChange>\n")
          .append("<Notification>").append(notification).append("</Notification>\n")
          .append("<AccessListChange>").append(accessListChange).append("</AccessListChange>\n")
          .append("<AccessList soap-enc:arrayType=\"xsd:string[").append(accessList.length).append("]\">\n");
        for (String access : accessList) {
            sb.append("<string>").append(escape(access)).append("</string>\n");
        }
        sb.append("</AccessList>\n</SetParameterAttributesStruct>\n</ParameterList>\n</cwmp:SetParameterAttributes>\n");
        return end(sb);
    }

    public static String addObject(String cwmpId, String objectPath, String parameterKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:AddObject>\n<ObjectName>").append(escape(objectPath))
          .append("</ObjectName>\n<ParameterKey>").append(escape(parameterKey))
          .append("</ParameterKey>\n</cwmp:AddObject>\n");
        return end(sb);
    }

    public static String deleteObject(String cwmpId, String objectPath, String parameterKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:DeleteObject>\n<ObjectName>").append(escape(objectPath))
          .append("</ObjectName>\n<ParameterKey>").append(escape(parameterKey))
          .append("</ParameterKey>\n</cwmp:DeleteObject>\n");
        return end(sb);
    }

    public static String download(String cwmpId, String commandKey, String url,
                                  String fileType, int fileSize, int delaySeconds) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:Download>\n<CommandKey>").append(escape(commandKey)).append("</CommandKey>\n")
          .append("<FileType>").append(escape(fileType)).append("</FileType>\n")
          .append("<URL>").append(escape(url)).append("</URL>\n")
          .append("<Username></Username>\n<Password></Password>\n")
          .append("<FileSize>").append(fileSize).append("</FileSize>\n")
          .append("<TargetFileName></TargetFileName>\n")
          .append("<DelaySeconds>").append(delaySeconds).append("</DelaySeconds>\n")
          .append("<SuccessURL></SuccessURL>\n<FailureURL></FailureURL>\n</cwmp:Download>\n");
        return end(sb);
    }

    public static String reboot(String cwmpId, String commandKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:Reboot>\n<CommandKey>").append(escape(commandKey)).append("</CommandKey>\n</cwmp:Reboot>\n");
        return end(sb);
    }

    public static String factoryReset(String cwmpId) {
        return end(envelope(cwmpId).append("<cwmp:FactoryReset/>\n"));
    }

    public static String scheduleInform(String cwmpId, int delaySeconds, String commandKey) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:ScheduleInform>\n<DelaySeconds>").append(delaySeconds)
          .append("</DelaySeconds>\n<CommandKey>").append(escape(commandKey))
          .append("</CommandKey>\n</cwmp:ScheduleInform>\n");
        return end(sb);
    }

    public static String getRpcMethods(String cwmpId) {
        return end(envelope(cwmpId).append("<cwmp:GetRPCMethods/>\n"));
    }

    public static String getQueuedTransfers(String cwmpId) {
        return end(envelope(cwmpId).append("<cwmp:GetQueuedTransfers/>\n"));
    }

    public static String getAllQueuedTransfers(String cwmpId) {
        return end(envelope(cwmpId).append("<cwmp:GetAllQueuedTransfers/>\n"));
    }

    public static String getOptions(String cwmpId, String optionName) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:GetOptions>\n<OptionName>").append(escape(optionName))
          .append("</OptionName>\n</cwmp:GetOptions>\n");
        return end(sb);
    }

    public static String setVouchers(String cwmpId, String... vouchers) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:SetVouchers>\n<VoucherList soap-enc:arrayType=\"xsd:base64[")
          .append(vouchers.length).append("]\">\n");
        for (String voucher : vouchers) {
            sb.append("<string>").append(escape(voucher)).append("</string>\n");
        }
        sb.append("</VoucherList>\n</cwmp:SetVouchers>\n");
        return end(sb);
    }

    public static String upload(String cwmpId, String commandKey, String url,
                                String fileType, int delaySeconds) {
        StringBuilder sb = envelope(cwmpId);
        sb.append("<cwmp:Upload>\n<CommandKey>").append(escape(commandKey)).append("</CommandKey>\n")
          .append("<FileType>").append(escape(fileType)).append("</FileType>\n")
          .append("<URL>").append(escape(url)).append("</URL>\n")
          .append("<Username></Username>\n<Password></Password>\n")
          .append("<DelaySeconds>").append(delaySeconds).append("</DelaySeconds>\n</cwmp:Upload>\n");
        return end(sb);
    }

    public static String transferCompleteResponse(String cwmpId) {
        return end(envelope(cwmpId).append("<cwmp:TransferCompleteResponse/>\n"));
    }

    /** An RPC the simulator must reject with fault 9000. */
    public static String unknownMethod(String cwmpId, String methodName) {
        return end(envelope(cwmpId).append("<cwmp:").append(methodName).append("/>\n"));
    }
}
