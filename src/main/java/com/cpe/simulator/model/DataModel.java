package com.cpe.simulator.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataModel {

    private ParameterNode root;
    private Map<String, ParameterNode> defaultValues;

    public DataModel() {
        this.root = new ParameterNode("", "");
        this.defaultValues = new LinkedHashMap<>();
    }

    public ParameterNode getRoot() {
        return root;
    }

    public void setRoot(ParameterNode root) {
        this.root = root;
    }

    public ParameterNode getNode(String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        String cleanPath = path.endsWith(".") ? path.substring(0, path.length() - 1) : path;
        String[] parts = cleanPath.split("\\.");
        ParameterNode current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            ParameterNode child = current.getChild(part);
            if (child == null) {
                return null;
            }
            current = child;
        }
        return current;
    }

    public ParameterValue getValue(String path) {
        ParameterNode node = getNode(path);
        if (node == null) {
            return null;
        }
        return node.getValue();
    }

    public void setValue(String path, String value) {
        ParameterNode node = getNode(path);
        if (node == null) {
            throw new IllegalArgumentException("Parameter not found: " + path);
        }
        if (node.getValue() == null) {
            throw new IllegalArgumentException("Cannot set value on container node: " + path);
        }
        node.getValue().setValue(value);
    }

    public boolean isWritable(String path) {
        ParameterNode node = getNode(path);
        if (node == null) {
            return false;
        }
        return node.isWritable();
    }

    public List<String> getParameterNames(String path, boolean nextLevel) {
        ParameterNode node = getNode(path);
        if (node == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (nextLevel) {
            for (ParameterNode child : node.getChildren().values()) {
                String childPath = child.getFullPath();
                if (!child.isLeaf()) {
                    childPath = childPath + ".";
                }
                result.add(childPath);
            }
        } else {
            collectAllDescendants(node, result);
        }
        return result;
    }

    private void collectAllDescendants(ParameterNode node, List<String> result) {
        for (ParameterNode child : node.getChildren().values()) {
            if (child.isLeaf()) {
                result.add(child.getFullPath());
            } else {
                result.add(child.getFullPath() + ".");
                collectAllDescendants(child, result);
            }
        }
    }

    public List<ParameterNode> getParameterValues(List<String> paths) {
        List<ParameterNode> result = new ArrayList<>();
        for (String path : paths) {
            ParameterNode node = getNode(path);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    public ParameterNode addInstance(String parentPath) {
        ParameterNode parent = getNode(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("Parent not found: " + parentPath);
        }
        if (!parent.isMultiInstance()) {
            throw new IllegalArgumentException("Not a multi-instance object: " + parentPath);
        }

        Integer recycled = parent.pollAvailableInstanceNumber();
        int instanceNum = (recycled != null) ? recycled : parent.getNextInstanceNumber();
        if (recycled == null) {
            parent.setNextInstanceNumber(instanceNum + 1);
        }

        String instanceName = String.valueOf(instanceNum);
        String instancePath = parent.getFullPath().isEmpty()
            ? instanceName
            : parent.getFullPath() + "." + instanceName;

        ParameterNode instanceNode = new ParameterNode(instanceName, instancePath);
        instanceNode.setMultiInstance(false);

        ParameterNode template = parent.getChild("1");
        if (template != null) {
            ParameterNode clonedTemplate = template.deepCopy();
            String oldPrefix = template.getFullPath();
            String newPrefix = instanceNode.getFullPath();
            for (ParameterNode child : clonedTemplate.getChildren().values()) {
                rewriteInstancePaths(child, oldPrefix, newPrefix);
                instanceNode.addChild(child);
            }
        }

        parent.addChild(instanceNode);
        return instanceNode;
    }

    public void deleteInstance(String instancePath) {
        String cleanPath = instancePath.endsWith(".")
            ? instancePath.substring(0, instancePath.length() - 1)
            : instancePath;
        int lastDot = cleanPath.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalArgumentException("Invalid instance path: " + instancePath);
        }
        String parentPath = cleanPath.substring(0, lastDot);
        String instanceName = cleanPath.substring(lastDot + 1);
        ParameterNode parent = getNode(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("Parent not found: " + parentPath);
        }
        ParameterNode instance = parent.getChild(instanceName);
        if (instance == null) {
            throw new IllegalArgumentException("Instance not found: " + instancePath);
        }
        parent.removeChild(instanceName);
        try {
            int instanceNum = Integer.parseInt(instanceName);
            parent.addAvailableInstanceNumber(instanceNum);
        } catch (NumberFormatException ignored) {
            // non-numeric instance name, nothing to recycle
        }
    }

    private void rewriteInstancePaths(ParameterNode node, String oldPrefix, String newPrefix) {
        String path = node.getFullPath();
        if (path.startsWith(oldPrefix)) {
            node.setFullPath(newPrefix + path.substring(oldPrefix.length()));
        }
        for (ParameterNode child : node.getChildren().values()) {
            rewriteInstancePaths(child, oldPrefix, newPrefix);
        }
    }

    public String getRootName() {
        Map<String, ParameterNode> children = root.getChildren();
        if (children.size() == 1) {
            return children.values().iterator().next().getName();
        }
        return "";
    }

    public String resolvePath(String relativePath) {
        String rootName = getRootName();
        if (rootName.isEmpty()) {
            return relativePath;
        }
        return rootName + "." + relativePath;
    }

    public ParameterValue getRelativeValue(String relativePath) {
        return getValue(resolvePath(relativePath));
    }

    public void setRelativeValue(String relativePath, String value) {
        setValue(resolvePath(relativePath), value);
    }

    public DataModel deepCopy() {
        DataModel copy = new DataModel();
        copy.root = this.root.deepCopy();
        return copy;
    }

    public void saveDefaults() {
        this.defaultValues = new LinkedHashMap<>();
        saveDefaultsRecursive(root);
    }

    private void saveDefaultsRecursive(ParameterNode node) {
        if (node.getValue() != null) {
            ParameterNode snapshot = node.deepCopy();
            this.defaultValues.put(node.getFullPath(), snapshot);
        }
        for (ParameterNode child : node.getChildren().values()) {
            saveDefaultsRecursive(child);
        }
    }

    public void factoryReset() {
        for (Map.Entry<String, ParameterNode> entry : defaultValues.entrySet()) {
            ParameterNode current = getNode(entry.getKey());
            if (current != null && current.getValue() != null) {
                ParameterNode defaultNode = entry.getValue();
                current.setValue(defaultNode.getValue().deepCopy());
                current.setWritable(defaultNode.isWritable());
                current.setNotification(defaultNode.getNotification());
            }
        }
    }
}
