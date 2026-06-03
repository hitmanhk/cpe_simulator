package com.cpe.simulator.cwmp;

import com.cpe.simulator.common.IdGenerator;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;

import java.util.ArrayList;
import java.util.List;

public class InformHandler {

    private static final String[] INFORM_PARAMS = {
        "Device.DeviceInfo.Manufacturer",
        "Device.DeviceInfo.ProductClass",
        "Device.DeviceInfo.SerialNumber",
        "Device.DeviceInfo.HardwareVersion",
        "Device.DeviceInfo.SoftwareVersion",
        "Device.DeviceInfo.ProvisioningCode",
        "Device.DeviceInfo.UpTime",
        "Device.ManagementServer.ConnectionRequestURL",
        "Device.ManagementServer.ParameterKey"
    };

    private final SoapBuilder soapBuilder = new SoapBuilder();

    public String buildInform(DeviceContext device, String eventCode, String commandKey) {
        DataModel dm = device.getDataModel();

        SoapEnvelope.DeviceIdStruct deviceId = new SoapEnvelope.DeviceIdStruct(
            getParamValue(dm, "Device.DeviceInfo.Manufacturer"),
            device.getOui(),
            device.getProductClass(),
            device.getSerialNumber()
        );

        List<SoapEnvelope.EventStruct> events = new ArrayList<>();
        events.add(new SoapEnvelope.EventStruct(eventCode, commandKey != null ? commandKey : ""));

        List<SoapEnvelope.ParameterInfo> params = new ArrayList<>();
        for (String paramPath : INFORM_PARAMS) {
            ParameterValue val = dm.getValue(paramPath);
            if (val != null) {
                params.add(new SoapEnvelope.ParameterInfo(paramPath, val.getValue(), val.getType()));
            } else {
                params.add(new SoapEnvelope.ParameterInfo(paramPath, "", "xsd:string"));
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
