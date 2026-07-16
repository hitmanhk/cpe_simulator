package com.cpe.simulator.model;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A TR-069 data model.
 *
 * <p>Models loaded from YAML are ordinary mutable trees. Device models created
 * with {@link #sharedCopy()} retain that tree as read-only structure and keep
 * only per-device changes in a sparse overlay. This is the common case for a
 * simulator: hundreds of structural nodes are identical, while only a handful
 * of values contain device-specific template substitutions.</p>
 */
public class DataModel {

    private ParameterNode root;
    private Map<String, ParameterNode> defaultValues;

    private ParameterNode sharedRoot;
    private ConcurrentHashMap<String, NodeOverride> overrides;
    private Map<String, NodeOverride> sharedDefaults;

    private record NodeOverride(ParameterValue value, Boolean writable,
                                Integer notification, List<String> accessList) {
        private NodeOverride copy() {
            return new NodeOverride(
                value == null ? null : value.deepCopy(), writable, notification,
                accessList == null ? null : List.copyOf(accessList));
        }
    }

    public DataModel() {
        this.root = new ParameterNode("", "");
        this.defaultValues = new LinkedHashMap<>();
    }

    public ParameterNode getRoot() {
        return isShared() ? new SharedNode(this, sharedRoot) : root;
    }

    public void setRoot(ParameterNode root) {
        this.root = root;
        this.sharedRoot = null;
        this.overrides = null;
        this.sharedDefaults = null;
    }

    public ParameterNode getNode(String path) {
        ParameterNode structuralNode = findNode(isShared() ? sharedRoot : root, path);
        if (structuralNode == null || !isShared()) {
            return structuralNode;
        }
        return new SharedNode(this, structuralNode);
    }

    private static ParameterNode findNode(ParameterNode start, String path) {
        if (path == null || path.isEmpty()) {
            return start;
        }
        String cleanPath = path.endsWith(".") ? path.substring(0, path.length() - 1) : path;
        String[] parts = cleanPath.split("\\.");
        ParameterNode current = start;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            current = current.getChild(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public ParameterValue getValue(String path) {
        ParameterNode node = getNode(path);
        return node == null ? null : node.getValue();
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
        return node != null && node.isWritable();
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
        ensureMaterialized();
        ParameterNode parent = getNode(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("Parent not found: " + parentPath);
        }
        if (!parent.isMultiInstance()) {
            throw new IllegalArgumentException("Not a multi-instance object: " + parentPath);
        }

        Integer recycled = parent.pollAvailableInstanceNumber();
        int instanceNum = recycled != null ? recycled : parent.getNextInstanceNumber();
        if (recycled == null) {
            parent.setNextInstanceNumber(instanceNum + 1);
        }

        String instanceName = String.valueOf(instanceNum);
        String instancePath = parent.getFullPath().isEmpty()
            ? instanceName
            : parent.getFullPath() + "." + instanceName;

        ParameterNode instanceNode = new ParameterNode(instanceName, instancePath);
        ParameterNode template = parent.getChild("1");
        if (template != null) {
            ParameterNode clonedTemplate = template.deepCopy();
            String oldPrefix = template.getFullPath();
            for (ParameterNode child : clonedTemplate.getChildren().values()) {
                rewriteInstancePaths(child, oldPrefix, instancePath);
                instanceNode.addChild(child);
            }
        }

        parent.addChild(instanceNode);
        return instanceNode;
    }

    public void deleteInstance(String instancePath) {
        ensureMaterialized();
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
        if (parent.getChild(instanceName) == null) {
            throw new IllegalArgumentException("Instance not found: " + instancePath);
        }
        parent.removeChild(instanceName);
        try {
            parent.addAvailableInstanceNumber(Integer.parseInt(instanceName));
        } catch (NumberFormatException ignored) {
            // Non-numeric instance names are not recycled.
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
        Map<String, ParameterNode> children = getRoot().getChildren();
        if (children.size() == 1) {
            return children.values().iterator().next().getName();
        }
        return "";
    }

    public String resolvePath(String relativePath) {
        String rootName = getRootName();
        return rootName.isEmpty() ? relativePath : rootName + "." + relativePath;
    }

    public ParameterValue getRelativeValue(String relativePath) {
        return getValue(resolvePath(relativePath));
    }

    public void setRelativeValue(String relativePath, String value) {
        setValue(resolvePath(relativePath), value);
    }

    /**
     * Creates a lightweight device model that shares this model's tree.
     * Structural mutations detach only that device into a regular mutable tree.
     */
    public DataModel sharedCopy() {
        DataModel copy = new DataModel();
        if (isShared()) {
            copy.sharedRoot = sharedRoot;
            copy.overrides = copyOverrides(overrides);
            copy.sharedDefaults = copyOverrides(sharedDefaults);
        } else {
            copy.sharedRoot = root;
            copy.overrides = new ConcurrentHashMap<>();
            copy.sharedDefaults = Map.of();
        }
        copy.root = null;
        copy.defaultValues = new LinkedHashMap<>();
        return copy;
    }

    public DataModel deepCopy() {
        DataModel copy = new DataModel();
        copy.root = isShared() ? copySharedNode(sharedRoot, overrides) : root.deepCopy();
        if (isShared()) {
            saveSharedDefaultsRecursive(sharedRoot, sharedDefaults, copy.defaultValues);
        } else {
            for (Map.Entry<String, ParameterNode> entry : defaultValues.entrySet()) {
                copy.defaultValues.put(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return copy;
    }

    public void saveDefaults() {
        if (isShared()) {
            sharedDefaults = copyOverrides(overrides);
            return;
        }
        defaultValues = new LinkedHashMap<>();
        saveDefaultsRecursive(root);
    }

    private void saveDefaultsRecursive(ParameterNode node) {
        if (node.getValue() != null) {
            defaultValues.put(node.getFullPath(), node.deepCopy());
        }
        for (ParameterNode child : node.getChildren().values()) {
            saveDefaultsRecursive(child);
        }
    }

    public void factoryReset() {
        if (isShared()) {
            overrides.clear();
            overrides.putAll(copyOverrides(sharedDefaults));
            return;
        }
        for (Map.Entry<String, ParameterNode> entry : defaultValues.entrySet()) {
            ParameterNode current = getNode(entry.getKey());
            if (current != null && current.getValue() != null) {
                ParameterNode defaultNode = entry.getValue();
                current.setValue(defaultNode.getValue().deepCopy());
                current.setWritable(defaultNode.isWritable());
                current.setNotification(defaultNode.getNotification());
                current.setAccessList(defaultNode.getAccessList());
            }
        }
    }

    boolean isStructureShared() {
        return isShared();
    }

    int overrideCount() {
        return isShared() ? overrides.size() : 0;
    }

    boolean sharesStructureWith(DataModel other) {
        return other != null && isShared() && other.isShared() && sharedRoot == other.sharedRoot;
    }

    private boolean isShared() {
        return sharedRoot != null;
    }

    private static ConcurrentHashMap<String, NodeOverride> copyOverrides(
            Map<String, NodeOverride> source) {
        ConcurrentHashMap<String, NodeOverride> copy = new ConcurrentHashMap<>();
        if (source != null) {
            source.forEach((path, state) -> copy.put(path, state.copy()));
        }
        return copy;
    }

    private ParameterValue effectiveValue(ParameterNode node) {
        NodeOverride state = overrides.get(node.getFullPath());
        return state != null && state.value() != null ? state.value() : node.getValue();
    }

    private boolean effectiveWritable(ParameterNode node) {
        NodeOverride state = overrides.get(node.getFullPath());
        return state != null && state.writable() != null ? state.writable() : node.isWritable();
    }

    private int effectiveNotification(ParameterNode node) {
        NodeOverride state = overrides.get(node.getFullPath());
        return state != null && state.notification() != null
            ? state.notification()
            : node.getNotification();
    }

    private List<String> effectiveAccessList(ParameterNode node) {
        NodeOverride state = overrides.get(node.getFullPath());
        return state != null && state.accessList() != null
            ? state.accessList()
            : node.getAccessList();
    }

    private void setValueOverride(ParameterNode node, ParameterValue value) {
        String path = node.getFullPath();
        overrides.compute(path, (ignored, old) -> new NodeOverride(
            value == null ? null : value.deepCopy(),
            old == null ? null : old.writable(),
            old == null ? null : old.notification(),
            old == null ? null : old.accessList()));
    }

    private void mutateValueOverride(ParameterNode node, String value, String type, boolean setType) {
        String path = node.getFullPath();
        overrides.compute(path, (ignored, old) -> {
            ParameterValue current = old != null && old.value() != null
                ? old.value().deepCopy()
                : node.getValue().deepCopy();
            current.setValue(value);
            if (setType) {
                current.setType(type);
            }
            return new NodeOverride(current,
                old == null ? null : old.writable(),
                old == null ? null : old.notification(),
                old == null ? null : old.accessList());
        });
    }

    private void setWritableOverride(ParameterNode node, boolean writable) {
        String path = node.getFullPath();
        overrides.compute(path, (ignored, old) -> new NodeOverride(
            old == null ? null : old.value(), writable,
            old == null ? null : old.notification(),
            old == null ? null : old.accessList()));
    }

    private void setNotificationOverride(ParameterNode node, int notification) {
        String path = node.getFullPath();
        overrides.compute(path, (ignored, old) -> new NodeOverride(
            old == null ? null : old.value(),
            old == null ? null : old.writable(), notification,
            old == null ? null : old.accessList()));
    }

    private void setAccessListOverride(ParameterNode node, List<String> accessList) {
        String path = node.getFullPath();
        overrides.compute(path, (ignored, old) -> new NodeOverride(
            old == null ? null : old.value(),
            old == null ? null : old.writable(),
            old == null ? null : old.notification(),
            accessList == null ? List.of() : List.copyOf(accessList)));
    }

    private void ensureMaterialized() {
        if (!isShared()) {
            return;
        }
        ParameterNode base = sharedRoot;
        Map<String, NodeOverride> currentStates = overrides;
        Map<String, NodeOverride> defaultStates = sharedDefaults;
        root = copySharedNode(base, currentStates);
        defaultValues = new LinkedHashMap<>();
        saveSharedDefaultsRecursive(base, defaultStates, defaultValues);
        sharedRoot = null;
        overrides = null;
        sharedDefaults = null;
    }

    private static void saveSharedDefaultsRecursive(ParameterNode node,
                                                    Map<String, NodeOverride> states,
                                                    Map<String, ParameterNode> target) {
        if (node.getValue() != null) {
            ParameterNode snapshot = copySharedNode(node, states);
            target.put(node.getFullPath(), snapshot);
        }
        for (ParameterNode child : node.getChildren().values()) {
            saveSharedDefaultsRecursive(child, states, target);
        }
    }

    private static ParameterNode copySharedNode(ParameterNode source, Map<String, NodeOverride> states) {
        ParameterNode copy = new ParameterNode(source.getName(), source.getFullPath());
        NodeOverride state = states == null ? null : states.get(source.getFullPath());
        copy.setWritable(state != null && state.writable() != null
            ? state.writable() : source.isWritable());
        copy.setNotification(state != null && state.notification() != null
            ? state.notification() : source.getNotification());
        copy.setAccessList(state != null && state.accessList() != null
            ? state.accessList() : source.getAccessList());
        copy.setMultiInstance(source.isMultiInstance());
        copy.setNextInstanceNumber(source.getNextInstanceNumber());
        ParameterValue value = state != null && state.value() != null ? state.value() : source.getValue();
        if (value != null) {
            copy.setValue(value.deepCopy());
        }
        for (ParameterNode child : source.getChildren().values()) {
            copy.addChild(copySharedNode(child, states));
        }
        return copy;
    }

    private static final class SharedNode extends ParameterNode {
        private final DataModel model;
        private final ParameterNode node;

        private SharedNode(DataModel model, ParameterNode node) {
            this.model = model;
            this.node = node;
        }

        @Override public String getName() { return node.getName(); }
        @Override public String getFullPath() { return node.getFullPath(); }
        @Override public boolean isWritable() { return model.effectiveWritable(node); }
        @Override public int getNotification() { return model.effectiveNotification(node); }
        @Override public List<String> getAccessList() { return model.effectiveAccessList(node); }
        @Override public boolean isMultiInstance() { return node.isMultiInstance(); }
        @Override public int getNextInstanceNumber() { return node.getNextInstanceNumber(); }
        @Override public boolean isLeaf() { return node.isLeaf(); }

        @Override
        public ParameterValue getValue() {
            return node.getValue() == null ? null : new SharedValue(model, node);
        }

        @Override
        public void setValue(ParameterValue value) {
            model.setValueOverride(node, value);
        }

        @Override public void setWritable(boolean writable) { model.setWritableOverride(node, writable); }
        @Override public void setNotification(int notification) { model.setNotificationOverride(node, notification); }
        @Override public void setAccessList(List<String> accessList) { model.setAccessListOverride(node, accessList); }

        @Override
        public ParameterNode getChild(String name) {
            ParameterNode child = node.getChild(name);
            return child == null ? null : new SharedNode(model, child);
        }

        @Override
        public Map<String, ParameterNode> getChildren() {
            return new SharedChildrenMap(model, node.getChildren());
        }

        @Override
        public ParameterNode deepCopy() {
            return copySharedNode(node, model.overrides);
        }

        @Override public String toString() { return node.toString(); }
    }

    private static final class SharedValue extends ParameterValue {
        private final DataModel model;
        private final ParameterNode node;

        private SharedValue(DataModel model, ParameterNode node) {
            this.model = model;
            this.node = node;
        }

        @Override public String getValue() { return model.effectiveValue(node).getValue(); }
        @Override public String getType() { return model.effectiveValue(node).getType(); }
        @Override public void setValue(String value) { model.mutateValueOverride(node, value, null, false); }
        @Override public void setType(String type) {
            model.mutateValueOverride(node, getValue(), type, true);
        }
        @Override public ParameterValue deepCopy() { return model.effectiveValue(node).deepCopy(); }
        @Override public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ParameterValue that)) {
                return false;
            }
            return Objects.equals(getValue(), that.getValue())
                && Objects.equals(getType(), that.getType());
        }
        @Override public int hashCode() { return Objects.hash(getValue(), getType()); }
        @Override public String toString() { return deepCopy().toString(); }
    }

    private static final class SharedChildrenMap extends AbstractMap<String, ParameterNode> {
        private final DataModel model;
        private final Map<String, ParameterNode> children;

        private SharedChildrenMap(DataModel model, Map<String, ParameterNode> children) {
            this.model = model;
            this.children = children;
        }

        @Override public int size() { return children.size(); }
        @Override public boolean isEmpty() { return children.isEmpty(); }
        @Override public boolean containsKey(Object key) { return children.containsKey(key); }

        @Override
        public ParameterNode get(Object key) {
            ParameterNode child = children.get(key);
            return child == null ? null : new SharedNode(model, child);
        }

        @Override
        public Set<Entry<String, ParameterNode>> entrySet() {
            return new AbstractSet<>() {
                @Override public int size() { return children.size(); }

                @Override
                public Iterator<Entry<String, ParameterNode>> iterator() {
                    Iterator<Entry<String, ParameterNode>> delegate = children.entrySet().iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return delegate.hasNext(); }

                        @Override
                        public Entry<String, ParameterNode> next() {
                            Entry<String, ParameterNode> entry = delegate.next();
                            return new SimpleImmutableEntry<>(entry.getKey(),
                                new SharedNode(model, entry.getValue()));
                        }
                    };
                }
            };
        }
    }
}
