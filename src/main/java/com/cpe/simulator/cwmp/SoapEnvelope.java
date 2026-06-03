package com.cpe.simulator.cwmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoapEnvelope {

    private String cwmpId;
    private String methodName;
    private Map<String, String> parameters;
    private List<ParameterInfo> parameterList;
    private List<EventStruct> events;
    private DeviceIdStruct deviceId;
    private int holdRequests;
    private boolean isFault;
    private int faultCode;
    private String faultString;
    private String rawBody;
    private List<String> stringList;
    private List<SoapBuilder.ParameterAttribute> attributeList;
    private List<SoapBuilder.QueuedTransfer> transferList;

    public SoapEnvelope() {
        this.parameters = new HashMap<>();
        this.parameterList = new ArrayList<>();
        this.events = new ArrayList<>();
    }

    public String getCwmpId() {
        return cwmpId;
    }

    public void setCwmpId(String cwmpId) {
        this.cwmpId = cwmpId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public List<ParameterInfo> getParameterList() {
        return parameterList;
    }

    public void setParameterList(List<ParameterInfo> parameterList) {
        this.parameterList = parameterList;
    }

    public List<EventStruct> getEvents() {
        return events;
    }

    public void setEvents(List<EventStruct> events) {
        this.events = events;
    }

    public DeviceIdStruct getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(DeviceIdStruct deviceId) {
        this.deviceId = deviceId;
    }

    public int getHoldRequests() {
        return holdRequests;
    }

    public void setHoldRequests(int holdRequests) {
        this.holdRequests = holdRequests;
    }

    public boolean isFault() {
        return isFault;
    }

    public void setFault(boolean fault) {
        isFault = fault;
    }

    public int getFaultCode() {
        return faultCode;
    }

    public void setFaultCode(int faultCode) {
        this.faultCode = faultCode;
    }

    public String getFaultString() {
        return faultString;
    }

    public void setFaultString(String faultString) {
        this.faultString = faultString;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = stringList;
    }

    public List<SoapBuilder.ParameterAttribute> getAttributeList() {
        return attributeList;
    }

    public void setAttributeList(List<SoapBuilder.ParameterAttribute> attributeList) {
        this.attributeList = attributeList;
    }

    public List<SoapBuilder.QueuedTransfer> getTransferList() {
        return transferList;
    }

    public void setTransferList(List<SoapBuilder.QueuedTransfer> transferList) {
        this.transferList = transferList;
    }

    public record ParameterInfo(String name, String value, String type) {}

    public record EventStruct(String eventCode, String commandKey) {}

    public record DeviceIdStruct(String manufacturer, String oui, String productClass, String serialNumber) {}
}
