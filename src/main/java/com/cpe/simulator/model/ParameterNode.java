package com.cpe.simulator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
    // Lowest-number-first recycling for deleted instance numbers (TreeSet.pollFirst)
    private TreeSet<Integer> availableInstanceNumbers;

    public ParameterNode() {
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
        return accessList != null ? accessList : Collections.emptyList();
    }

    public void setAccessList(List<String> accessList) {
        this.accessList = accessList == null || accessList.isEmpty()
            ? null
            : new ArrayList<>(accessList);
    }

    public Map<String, ParameterNode> getChildren() {
        return children != null ? children : Collections.emptyMap();
    }

    public void setChildren(Map<String, ParameterNode> children) {
        this.children = children == null || children.isEmpty() ? null : children;
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

    void addAvailableInstanceNumber(int num) {
        if (availableInstanceNumbers == null) {
            availableInstanceNumbers = new TreeSet<>();
        }
        availableInstanceNumbers.add(num);
    }

    Integer pollAvailableInstanceNumber() {
        return availableInstanceNumbers != null ? availableInstanceNumbers.pollFirst() : null;
    }

    public void addChild(ParameterNode child) {
        if (this.children == null) {
            this.children = new LinkedHashMap<>();
        }
        this.children.put(child.getName(), child);
    }

    public ParameterNode getChild(String name) {
        return this.children != null ? this.children.get(name) : null;
    }

    public void removeChild(String name) {
        if (this.children != null) {
            this.children.remove(name);
            if (this.children.isEmpty()) {
                this.children = null;
            }
        }
    }

    public boolean isLeaf() {
        return this.children == null || this.children.isEmpty();
    }

    public ParameterNode deepCopy() {
        ParameterNode copy = new ParameterNode(this.name, this.fullPath);
        copy.writable = this.writable;
        copy.notification = this.notification;
        copy.accessList = this.accessList == null ? null : new ArrayList<>(this.accessList);
        copy.multiInstance = this.multiInstance;
        copy.nextInstanceNumber = this.nextInstanceNumber;
        copy.availableInstanceNumbers = this.availableInstanceNumbers == null
            ? null
            : new TreeSet<>(this.availableInstanceNumbers);
        if (this.value != null) {
            copy.value = this.value.deepCopy();
        }
        if (this.children != null) {
            copy.children = new LinkedHashMap<>();
            for (Map.Entry<String, ParameterNode> entry : this.children.entrySet()) {
                copy.children.put(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return copy;
    }

    @Override
    public String toString() {
        return "ParameterNode{name='" + name + "', fullPath='" + fullPath + "', leaf=" + isLeaf() + "}";
    }
}
