package com.cpe.simulator.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModelLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static DataModel load(String modelSpec) {
        if ("tr181".equalsIgnoreCase(modelSpec)) {
            return loadFromFile(resolveModelPath("tr181.yaml"));
        } else if ("tr098".equalsIgnoreCase(modelSpec)) {
            return loadFromFile(resolveModelPath("tr098.yaml"));
        } else {
            return loadFromFile(modelSpec);
        }
    }

    public static DataModel loadFromFile(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("Model file not found: " + path.toAbsolutePath()
                    + ". Expected models/tr181.yaml or models/tr098.yaml in the same directory as the JAR file.");
        }
        try (InputStream is = Files.newInputStream(path)) {
            JsonNode root = YAML_MAPPER.readTree(is);
            return buildDataModel(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from file: " + filePath, e);
        }
    }

    private static String resolveModelPath(String modelName) {
        return resolveBaseDir().resolve("models").resolve(modelName).toString();
    }

    private static Path resolveBaseDir() {
        try {
            Path jarPath = Path.of(ModelLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isDirectory(jarPath)) {
                return Path.of(System.getProperty("user.dir"));
            }
            return jarPath.getParent();
        } catch (URISyntaxException e) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    private static DataModel buildDataModel(JsonNode root) {
        DataModel dataModel = new DataModel();
        JsonNode parameters = root.get("parameters");
        if (parameters == null || !parameters.isArray()) {
            dataModel.saveDefaults();
            return dataModel;
        }

        List<ParameterDefinition> defs = new ArrayList<>();
        for (JsonNode param : parameters) {
            ParameterDefinition def = new ParameterDefinition();
            def.path = param.get("path").asText();
            def.type = param.has("type") ? param.get("type").asText() : "xsd:string";
            def.value = param.has("value") ? param.get("value").asText() : "";
            def.writable = param.has("writable") && param.get("writable").asBoolean();
            def.notification = param.has("notification") ? param.get("notification").asInt() : 0;
            def.multiInstance = param.has("multiInstance") && param.get("multiInstance").asBoolean();
            defs.add(def);
        }

        for (ParameterDefinition def : defs) {
            ensurePath(dataModel, def);
        }

        dataModel.saveDefaults();
        return dataModel;
    }

    private static void ensurePath(DataModel dataModel, ParameterDefinition def) {
        String[] parts = def.path.split("\\.");
        ParameterNode current = dataModel.getRoot();

        for (int i = 0; i < parts.length; i++) {
            String partName = parts[i];
            boolean isLast = (i == parts.length - 1);
            String childPath = buildPath(parts, i);

            ParameterNode child = current.getChild(partName);
            if (child == null) {
                child = new ParameterNode(partName, childPath);
                current.addChild(child);
            }

            if (isLast) {
                child.setValue(new ParameterValue(def.value, def.type));
                child.setWritable(def.writable);
                child.setNotification(def.notification);
            }

            if (def.multiInstance && isInstanceNumber(partName)) {
                String parentPath = i > 0 ? buildPath(parts, i - 1) : "";
                ParameterNode parentNode = i > 0 ? dataModel.getNode(parentPath) : dataModel.getRoot();
                if (parentNode != null) {
                    parentNode.setMultiInstance(true);
                    int num = Integer.parseInt(partName);
                    if (num >= parentNode.getNextInstanceNumber()) {
                        parentNode.setNextInstanceNumber(num + 1);
                    }
                }
            }

            current = child;
        }
    }

    private static boolean isInstanceNumber(String name) {
        try {
            Integer.parseInt(name);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String buildPath(String[] parts, int upToIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= upToIndex; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static class ParameterDefinition {
        String path;
        String type;
        String value;
        boolean writable;
        int notification;
        boolean multiInstance;
    }
}