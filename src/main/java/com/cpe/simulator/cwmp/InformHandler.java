package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.IdGenerator;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;

import java.util.ArrayList;
import java.util.List;

public class InformHandler {

    private static final String[] INFORM_PARAMS = {
        "DeviceInfo.Manufacturer",
        "DeviceInfo.ProductClass",
        "DeviceInfo.SerialNumber",
        "DeviceInfo.HardwareVersion",
        "DeviceInfo.SoftwareVersion",
        "DeviceInfo.ProvisioningCode",
        "DeviceInfo.UpTime",
        "ManagementServer.ConnectionRequestURL",
        "ManagementServer.ParameterKey"
    };

    private final SoapBuilder soapBuilder = new SoapBuilder();

    public String buildInform(DeviceContext device, String eventCode, String commandKey) {
        DataModel dm = device.getDataModel();

        SoapEnvelope.DeviceIdStruct deviceId = new SoapEnvelope.DeviceIdStruct(
            dm.getRelativeValue("DeviceInfo.Manufacturer") != null ? dm.getRelativeValue("DeviceInfo.Manufacturer").getValue() : "",
            device.getOui(),
            device.getProductClass(),
            device.getSerialNumber()
        );

        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct(eventCode, commandKey != null ? commandKey : ""));

        List<SoapEnvelope.ParameterInfo> params = new ArrayList<>();
        for (String relPath : INFORM_PARAMS) {
            String absPath = dm.resolvePath(relPath);
            ParameterValue val = dm.getValue(absPath);
            if (val != null) {
                params.add(new SoapEnvelope.ParameterInfo(absPath, val.getValue(), val.getType()));
            } else {
                params.add(new SoapEnvelope.ParameterInfo(absPath, "", "xsd:string"));
            }
        }

        String cwmpId = IdGenerator.cwmpId();

        return soapBuilder.buildInform(deviceId, events, params, cwmpId);
    }

    private String getParamValue(DataModel dm, String path) {
        ParameterValue val = dm.getValue(path);
        return val != null ? val.getValue() : "";
    }
}
