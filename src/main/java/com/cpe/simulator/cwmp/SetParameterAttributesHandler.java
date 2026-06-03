package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;

import java.util.List;

public class SetParameterAttributesHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "SetParameterAttributes";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("SetParameterAttributesResponse");

        try {
            SoapParser.SetParameterAttributesRequest parsed =
                soapParser.parseSetParameterAttributes(request.getRawBody());
            DataModel dm = device.getDataModel();

            for (SoapParser.AttributeChange change : parsed.changes()) {
                ParameterNode node = dm.getNode(change.name());
                if (node == null) {
                    response.setFault(true);
                    response.setFaultCode(9005);
                    response.setFaultString("Invalid parameter name: " + change.name());
                    return response;
                }
                node.setNotification(change.notification());
                if (change.accessList() != null && !change.accessList().isEmpty()) {
                    node.setAccessList(change.accessList());
                }
            }

            response.getParameters().put("Status", "0");
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
