package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;

import java.util.ArrayList;
import java.util.List;

public class GetParameterAttributesHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "GetParameterAttributes";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetParameterAttributesResponse");

        try {
            SoapParser.GetParameterAttributesRequest parsed =
                soapParser.parseGetParameterAttributes(request.getRawBody());
            DataModel dm = device.getDataModel();

            List<SoapBuilder.ParameterAttribute> attrs = new ArrayList<>();
            for (String path : parsed.parameterPaths()) {
                ParameterNode node = dm.getNode(path);
                if (node == null) {
                    response.setFault(true);
                    response.setFaultCode(9005);
                    response.setFaultString("Invalid parameter name: " + path);
                    return response;
                }
                List<String> accessList = node.getAccessList();
                if (accessList == null || accessList.isEmpty()) {
                    accessList = List.of("Subscriber");
                }
                attrs.add(new SoapBuilder.ParameterAttribute(
                    node.getFullPath(), node.getNotification(), accessList));
            }

            response.getParameters().put("AttributeCount", String.valueOf(attrs.size()));
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
