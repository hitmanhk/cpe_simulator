package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;

public class AddObjectHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "AddObject";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("AddObjectResponse");

        try {
            SoapParser.AddObjectRequest parsed = soapParser.parseAddObject(request.getRawBody());
            DataModel dm = device.getDataModel();

            ParameterNode instance = dm.addInstance(parsed.objectPath());
            String instanceName = instance.getName();
            int instanceNumber = Integer.parseInt(instanceName);

            response.getParameters().put("InstanceNumber", String.valueOf(instanceNumber));
            response.getParameters().put("Status", "0");
        } catch (IllegalArgumentException e) {
            response.setFault(true);
            response.setFaultCode(9005);
            response.setFaultString(e.getMessage());
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
