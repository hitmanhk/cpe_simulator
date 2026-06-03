package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;

public class DeleteObjectHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "DeleteObject";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("DeleteObjectResponse");

        try {
            SoapParser.DeleteObjectRequest parsed = soapParser.parseDeleteObject(request.getRawBody());
            DataModel dm = device.getDataModel();

            dm.deleteInstance(parsed.objectPath());

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
