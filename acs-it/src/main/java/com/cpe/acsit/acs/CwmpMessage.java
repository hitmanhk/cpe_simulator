package com.cpe.acsit.acs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed CPE→ACS CWMP message (Inform, TransferComplete, an RPC response,
 * a SOAP Fault, or an empty HTTP POST body).
 */
public record CwmpMessage(String method, String cwmpId, XmlNode body, String rawXml) {

    /** Parses the SOAP envelope; empty body means an empty-request POST. */
    public static CwmpMessage parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return new CwmpMessage("", null, null, "");
        }
        XmlNode envelope = XmlNode.parse(xml);
        XmlNode header = envelope.first("Header");
        String cwmpId = header != null ? header.textOf("ID") : null;
        XmlNode bodyNode = envelope.first("Body");
        if (bodyNode == null || bodyNode.children().isEmpty()) {
            return new CwmpMessage("", cwmpId, bodyNode, xml);
        }
        XmlNode methodElement = bodyNode.children().get(0);
        return new CwmpMessage(methodElement.name(), cwmpId, methodElement, xml);
    }

    public boolean isEmptyRequest() {
        return method.isEmpty();
    }

    public boolean isFault() {
        return "Fault".equals(method);
    }

    public int faultCode() {
        XmlNode detail = body.first("detail");
        XmlNode fault = detail != null ? detail.first("Fault") : null;
        String code = fault != null ? fault.textOf("FaultCode") : null;
        return code != null ? Integer.parseInt(code.trim()) : -1;
    }

    public String faultString() {
        XmlNode detail = body.first("detail");
        XmlNode fault = detail != null ? detail.first("Fault") : null;
        return fault != null ? fault.textOf("FaultString") : null;
    }

    // ---------- Inform accessors ----------

    public String manufacturer() {
        return body.first("DeviceId") != null ? body.first("DeviceId").textOf("Manufacturer") : null;
    }

    public String oui() {
        return body.first("DeviceId") != null ? body.first("DeviceId").textOf("OUI") : null;
    }

    public String productClass() {
        return body.first("DeviceId") != null ? body.first("DeviceId").textOf("ProductClass") : null;
    }

    public String serialNumber() {
        return body.first("DeviceId") != null ? body.first("DeviceId").textOf("SerialNumber") : null;
    }

    /** Inform EventStruct codes in order (e.g. ["0 BOOTSTRAP"]). */
    public List<String> eventCodes() {
        List<String> codes = new ArrayList<>();
        XmlNode event = body.first("Event");
        if (event != null) {
            for (XmlNode struct : event.all("EventStruct")) {
                String code = struct.textOf("EventCode");
                if (code != null) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /** ParameterList as an ordered name→value map (Inform and GetParameterValuesResponse). */
    public Map<String, String> parameterValues() {
        Map<String, String> values = new LinkedHashMap<>();
        XmlNode list = body.first("ParameterList");
        if (list != null) {
            for (XmlNode struct : list.all("ParameterValueStruct")) {
                values.put(struct.textOf("Name"), struct.textOf("Value"));
            }
        }
        return values;
    }

    /** GetParameterNamesResponse entries: name→writable. */
    public Map<String, Boolean> parameterNames() {
        Map<String, Boolean> names = new LinkedHashMap<>();
        XmlNode list = body.first("ParameterList");
        if (list != null) {
            for (XmlNode struct : list.all("ParameterInfoStruct")) {
                names.put(struct.textOf("Name"),
                        "true".equalsIgnoreCase(struct.textOf("Writable")));
            }
        }
        return names;
    }

    /** GetParameterAttributesResponse entries: name→notification. */
    public Map<String, Integer> parameterAttributes() {
        Map<String, Integer> attrs = new LinkedHashMap<>();
        XmlNode list = body.first("ParameterList");
        if (list != null) {
            for (XmlNode struct : list.all("ParameterAttributeStruct")) {
                String notification = struct.textOf("Notification");
                attrs.put(struct.textOf("Name"),
                        notification != null ? Integer.parseInt(notification.trim()) : -1);
            }
        }
        return attrs;
    }

    /** GetRPCMethodsResponse method names. */
    public List<String> methodList() {
        List<String> methods = new ArrayList<>();
        XmlNode list = body.first("MethodList");
        if (list != null) {
            for (XmlNode item : list.all("string")) {
                methods.add(item.text());
            }
        }
        return methods;
    }

    /** GetQueuedTransfers / GetAllQueuedTransfers rows: CommandKey→State. */
    public Map<String, Integer> transfers() {
        Map<String, Integer> transfers = new LinkedHashMap<>();
        XmlNode list = body.first("TransferList");
        if (list != null) {
            for (XmlNode struct : list.children()) {
                String state = struct.textOf("State");
                transfers.put(struct.textOf("CommandKey"),
                        state != null ? Integer.parseInt(state.trim()) : -1);
            }
        }
        return transfers;
    }

    /** Simple child-text accessor for Status / InstanceNumber / CommandKey / etc. */
    public String field(String name) {
        return body != null ? body.textOf(name) : null;
    }

    public int intField(String name, int defaultValue) {
        String value = field(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }
}
