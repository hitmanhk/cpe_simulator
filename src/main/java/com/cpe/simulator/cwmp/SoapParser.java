package com.cpe.simulator.cwmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SoapParser {

    public record ParameterNamesRequest(String path, boolean nextLevel) {}
    public record DownloadRequest(String commandKey, String fileType, String url, int fileSize, String targetFileName) {}
    public record RebootRequest(String commandKey) {}
    public record FaultInfo(int faultCode, String faultString) {}
    public record ScheduleInformRequest(String commandKey, int delaySeconds) {}
    public record AddObjectRequest(String objectPath, String parameterKey) {}
    public record DeleteObjectRequest(String objectPath, String parameterKey) {}
    public record GetParameterAttributesRequest(List<String> parameterPaths) {}
    public record SetParameterAttributesRequest(List<AttributeChange> changes) {}
    public record AttributeChange(String name, int notification, List<String> accessList) {}

    private static final XmlMapper MAPPER = new XmlMapper();

    private JsonNode findNode(JsonNode parent, String localName) {
        if (parent == null) return null;
        Iterator<String> names = parent.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equals(localName) || name.endsWith(":" + localName)) {
                return parent.get(name);
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) return node.asText();
        if (node.isArray() && node.size() > 0) return node.get(0).asText();
        JsonNode textContent = node.get("");
        if (textContent != null && textContent.isTextual()) return textContent.asText();
        return node.asText();
    }

    private JsonNode getBody(JsonNode root) {
        JsonNode body = findNode(root, "Body");
        if (body != null) return body;
        JsonNode envelope = findNode(root, "Envelope");
        if (envelope != null) return findNode(envelope, "Body");
        return root;
    }

    private JsonNode getHeader(JsonNode root) {
        JsonNode header = findNode(root, "Header");
        if (header != null) return header;
        JsonNode envelope = findNode(root, "Envelope");
        if (envelope != null) return findNode(envelope, "Header");
        return null;
    }

    public SoapEnvelope parse(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        SoapEnvelope envelope = new SoapEnvelope();
        envelope.setRawBody(xml);

        JsonNode header = getHeader(root);
        if (header != null) {
            JsonNode idNode = findNode(header, "ID");
            if (idNode != null) {
                envelope.setCwmpId(textValue(idNode));
            }
        }

        JsonNode body = getBody(root);
        if (body == null) return envelope;

        JsonNode faultNode = findNode(body, "Fault");
        if (faultNode != null) {
            envelope.setFault(true);
            JsonNode detail = findNode(faultNode, "detail");
            if (detail != null) {
                JsonNode cwmpFault = findNode(detail, "Fault");
                if (cwmpFault != null) {
                    JsonNode fc = findNode(cwmpFault, "FaultCode");
                    JsonNode fs = findNode(cwmpFault, "FaultString");
                    if (fc != null) envelope.setFaultCode(Integer.parseInt(textValue(fc)));
                    if (fs != null) envelope.setFaultString(textValue(fs));
                }
            }
            return envelope;
        }

        Iterator<String> bodyFields = body.fieldNames();
        while (bodyFields.hasNext()) {
            String fieldName = bodyFields.next();
            String localName = fieldName.contains(":") ? fieldName.substring(fieldName.indexOf(":") + 1) : fieldName;
            envelope.setMethodName(localName);
            break;
        }

        return envelope;
    }

    public String extractMethodName(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        if (body == null) return null;

        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.contains(":")) {
                return name.substring(name.indexOf(":") + 1);
            }
            return name;
        }
        return null;
    }

    public String extractCwmpId(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode header = getHeader(root);
        if (header == null) return null;
        JsonNode idNode = findNode(header, "ID");
        return idNode != null ? textValue(idNode) : null;
    }

    public List<String> parseGetParameterValues(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "GetParameterValues");
        if (method == null) return List.of();

        JsonNode paramNames = findNode(method, "ParameterNames");
        if (paramNames == null) return List.of();

        List<String> result = new ArrayList<>();
        JsonNode stringNodes = findNode(paramNames, "string");
        if (stringNodes != null) {
            if (stringNodes.isArray()) {
                for (JsonNode n : stringNodes) {
                    result.add(n.asText());
                }
            } else {
                result.add(stringNodes.asText());
            }
        }
        return result;
    }

    public List<SoapEnvelope.ParameterInfo> parseSetParameterValues(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "SetParameterValues");
        if (method == null) return List.of();

        JsonNode paramList = findNode(method, "ParameterList");
        if (paramList == null) return List.of();

        List<SoapEnvelope.ParameterInfo> result = new ArrayList<>();
        JsonNode structs = findNode(paramList, "ParameterValueStruct");
        if (structs == null) return result;

        List<JsonNode> structList = new ArrayList<>();
        if (structs.isArray()) {
            structs.forEach(structList::add);
        } else {
            structList.add(structs);
        }

        for (JsonNode struct : structList) {
            String name = textValue(findNode(struct, "Name"));
            JsonNode valueNode = findNode(struct, "Value");
            String value = valueNode != null ? textValue(valueNode) : "";
            String type = "xsd:string";
            if (valueNode != null) {
                JsonNode typeAttr = valueNode.get("type");
                if (typeAttr != null) type = typeAttr.asText();
            }
            result.add(new SoapEnvelope.ParameterInfo(name, value, type));
        }
        return result;
    }

    public ParameterNamesRequest parseGetParameterNames(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "GetParameterNames");
        if (method == null) return new ParameterNamesRequest("", false);

        String path = textValue(findNode(method, "ParameterPath"));
        if (path == null) path = "";
        JsonNode nextLevelNode = findNode(method, "NextLevel");
        boolean nextLevel = nextLevelNode != null && Boolean.parseBoolean(textValue(nextLevelNode));
        return new ParameterNamesRequest(path, nextLevel);
    }

    public DownloadRequest parseDownload(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "Download");
        if (method == null) return new DownloadRequest("", "", "", 0, "");

        String commandKey = textValue(findNode(method, "CommandKey"));
        String fileType = textValue(findNode(method, "FileType"));
        String url = textValue(findNode(method, "URL"));
        JsonNode fileSizeNode = findNode(method, "FileSize");
        int fileSize = fileSizeNode != null ? Integer.parseInt(textValue(fileSizeNode)) : 0;
        String targetFileName = textValue(findNode(method, "TargetFileName"));

        return new DownloadRequest(
            commandKey != null ? commandKey : "",
            fileType != null ? fileType : "",
            url != null ? url : "",
            fileSize,
            targetFileName != null ? targetFileName : ""
        );
    }

    public RebootRequest parseReboot(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "Reboot");
        if (method == null) return new RebootRequest("");

        String commandKey = textValue(findNode(method, "CommandKey"));
        return new RebootRequest(commandKey != null ? commandKey : "");
    }

    public boolean parseInformResponse(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "InformResponse");
        if (method == null) return false;

        JsonNode maxEnvelopes = findNode(method, "MaxEnvelopes");
        if (maxEnvelopes == null) return false;
        return Integer.parseInt(textValue(maxEnvelopes)) > 0;
    }

    public FaultInfo parseFault(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode faultNode = findNode(body, "Fault");
        if (faultNode == null) return new FaultInfo(0, "");

        int faultCode = 0;
        String faultString = "";

        JsonNode detail = findNode(faultNode, "detail");
        if (detail != null) {
            JsonNode cwmpFault = findNode(detail, "Fault");
            if (cwmpFault != null) {
                JsonNode fc = findNode(cwmpFault, "FaultCode");
                JsonNode fs = findNode(cwmpFault, "FaultString");
                if (fc != null) faultCode = Integer.parseInt(textValue(fc));
                if (fs != null) faultString = textValue(fs);
            }
        }

        if (faultCode == 0) {
            JsonNode fc = findNode(faultNode, "faultcode");
            if (fc != null) faultString = textValue(fc);
        }

        return new FaultInfo(faultCode, faultString != null ? faultString : "");
    }

    public ScheduleInformRequest parseScheduleInform(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "ScheduleInform");
        if (method == null) return new ScheduleInformRequest("", 0);

        String commandKey = textValue(findNode(method, "CommandKey"));
        JsonNode delayNode = findNode(method, "DelaySeconds");
        int delay = delayNode != null ? Integer.parseInt(textValue(delayNode)) : 0;
        return new ScheduleInformRequest(commandKey != null ? commandKey : "", delay);
    }

    public AddObjectRequest parseAddObject(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "AddObject");
        if (method == null) return new AddObjectRequest("", "");

        String objectPath = textValue(findNode(method, "ObjectName"));
        String parameterKey = textValue(findNode(method, "ParameterKey"));
        return new AddObjectRequest(
            objectPath != null ? objectPath : "",
            parameterKey != null ? parameterKey : ""
        );
    }

    public DeleteObjectRequest parseDeleteObject(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "DeleteObject");
        if (method == null) return new DeleteObjectRequest("", "");

        String objectPath = textValue(findNode(method, "ObjectName"));
        String parameterKey = textValue(findNode(method, "ParameterKey"));
        return new DeleteObjectRequest(
            objectPath != null ? objectPath : "",
            parameterKey != null ? parameterKey : ""
        );
    }

    public GetParameterAttributesRequest parseGetParameterAttributes(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "GetParameterAttributes");
        if (method == null) return new GetParameterAttributesRequest(List.of());

        JsonNode paramPaths = findNode(method, "ParameterNames");
        if (paramPaths == null) return new GetParameterAttributesRequest(List.of());

        List<String> paths = new ArrayList<>();
        JsonNode stringNodes = findNode(paramPaths, "string");
        if (stringNodes != null) {
            if (stringNodes.isArray()) {
                stringNodes.forEach(n -> paths.add(n.asText()));
            } else {
                paths.add(stringNodes.asText());
            }
        }
        return new GetParameterAttributesRequest(paths);
    }

    public SetParameterAttributesRequest parseSetParameterAttributes(String xml) throws Exception {
        JsonNode root = MAPPER.readTree(xml);
        JsonNode body = getBody(root);
        JsonNode method = findNode(body, "SetParameterAttributes");
        if (method == null) return new SetParameterAttributesRequest(List.of());

        JsonNode paramList = findNode(method, "ParameterList");
        if (paramList == null) return new SetParameterAttributesRequest(List.of());

        List<AttributeChange> changes = new ArrayList<>();
        JsonNode structs = findNode(paramList, "SetParameterAttributesStruct");
        if (structs == null) return new SetParameterAttributesRequest(changes);

        List<JsonNode> structList = new ArrayList<>();
        if (structs.isArray()) {
            structs.forEach(structList::add);
        } else {
            structList.add(structs);
        }

        for (JsonNode struct : structList) {
            String name = textValue(findNode(struct, "Name"));
            JsonNode notifNode = findNode(struct, "Notification");
            int notification = notifNode != null ? Integer.parseInt(textValue(notifNode)) : 0;

            List<String> accessList = new ArrayList<>();
            JsonNode accessListNode = findNode(struct, "AccessList");
            if (accessListNode != null) {
                JsonNode stringNodes = findNode(accessListNode, "string");
                if (stringNodes != null) {
                    if (stringNodes.isArray()) {
                        stringNodes.forEach(n -> accessList.add(n.asText()));
                    } else {
                        accessList.add(stringNodes.asText());
                    }
                }
            }
            changes.add(new AttributeChange(name != null ? name : "", notification, accessList));
        }
        return new SetParameterAttributesRequest(changes);
    }
}
