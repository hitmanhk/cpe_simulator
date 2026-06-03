package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;

import java.util.ArrayList;
import java.util.List;

public class GetParameterValuesHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();
    private final SoapBuilder soapBuilder = new SoapBuilder();

    @Override
    public String methodName() {
        return "GetParameterValues";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetParameterValuesResponse");

        try {
            List<String> paramPaths = soapParser.parseGetParameterValues(request.getRawBody());
            DataModel dm = device.getDataModel();

            for (String path : paramPaths) {
                if (dm.getNode(path) == null) {
                    response.setFault(true);
                    response.setFaultCode(9005);
                    response.setFaultString("Invalid parameter name: " + path);
                    return response;
                }
            }

            List<SoapEnvelope.ParameterInfo> resultParams = new ArrayList<>();
            for (String path : paramPaths) {
                ParameterNode node = dm.getNode(path);
                if (node.isLeaf() && node.getValue() != null) {
                    resultParams.add(new SoapEnvelope.ParameterInfo(
                        node.getFullPath(), node.getValue().getValue(), node.getValue().getType()));
                } else {
                    collectLeafValues(node, resultParams);
                }
            }

            response.setParameterList(resultParams);
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9005);
            response.setFaultString(e.getMessage());
        }

        return response;
    }

    private void collectLeafValues(ParameterNode node, List<SoapEnvelope.ParameterInfo> result) {
        if (node.isLeaf() && node.getValue() != null) {
            result.add(new SoapEnvelope.ParameterInfo(
                node.getFullPath(), node.getValue().getValue(), node.getValue().getType()));
            return;
        }
        for (ParameterNode child : node.getChildren().values()) {
            collectLeafValues(child, result);
        }
    }
}
