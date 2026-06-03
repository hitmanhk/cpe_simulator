package com.cpe.simulator.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParameterNode {

    private String name;
    private String fullPath;
    private ParameterValue value;
    private boolean writable;
    private int notification;
    private List<String> accessList;
    private Map<String, ParameterNode> children;
    private boolean multiInstance;
    private int nextInstanceNumber;

    public ParameterNode() {
        this.children = new LinkedHashMap<>();
        this.accessList = new ArrayList<>();
        this.writable = false;
        this.notification = 0;
        this.multiInstance = false;
        this.nextInstanceNumber = 1;
    }

    public ParameterNode(String name, String fullPath) {
        this();
        this.name = name;
        this.fullPath = fullPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public ParameterValue getValue() {
        return value;
    }

    public void setValue(ParameterValue value) {
        this.value = value;
    }

    public boolean isWritable() {
        return writable;
    }

    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    public int getNotification() {
        return notification;
    }

    public void setNotification(int notification) {
        this.notification = notification;
    }

    public List<String> getAccessList() {
        return accessList;
    }

    public void setAccessList(List<String> accessList) {
        this.accessList = accessList != null ? accessList : new ArrayList<>();
    }

    public Map<String, ParameterNode> getChildren() {
        return children;
    }

    public void setChildren(Map<String, ParameterNode> children) {
        this.children = children;
    }

    public boolean isMultiInstance() {
        return multiInstance;
    }

    public void setMultiInstance(boolean multiInstance) {
        this.multiInstance = multiInstance;
    }

    public int getNextInstanceNumber() {
        return nextInstanceNumber;
    }

    public void setNextInstanceNumber(int nextInstanceNumber) {
        this.nextInstanceNumber = nextInstanceNumber;
    }

    public void addChild(ParameterNode child) {
        this.children.put(child.getName(), child);
    }

    public ParameterNode getChild(String name) {
        return this.children.get(name);
    }

    public void removeChild(String name) {
        this.children.remove(name);
    }

    public boolean isLeaf() {
        return this.children.isEmpty();
    }

    public ParameterNode deepCopy() {
        ParameterNode copy = new ParameterNode(this.name, this.fullPath);
        copy.writable = this.writable;
        copy.notification = this.notification;
        copy.accessList = new ArrayList<>(this.accessList);
        copy.multiInstance = this.multiInstance;
        copy.nextInstanceNumber = this.nextInstanceNumber;
        if (this.value != null) {
            copy.value = this.value.deepCopy();
        }
        for (Map.Entry<String, ParameterNode> entry : this.children.entrySet()) {
            copy.children.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return copy;
    }

    @Override
    public String toString() {
        return "ParameterNode{name='" + name + "', fullPath='" + fullPath + "', leaf=" + isLeaf() + "}";
    }
}
