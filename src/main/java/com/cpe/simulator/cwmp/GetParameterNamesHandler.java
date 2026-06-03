package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;

import java.util.List;

public class GetParameterNamesHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();
    private final SoapBuilder soapBuilder = new SoapBuilder();

    @Override
    public String methodName() {
        return "GetParameterNames";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetParameterNamesResponse");

        try {
            SoapParser.ParameterNamesRequest parsed = soapParser.parseGetParameterNames(request.getRawBody());
            DataModel dm = device.getDataModel();

            List<String> paramNames = dm.getParameterNames(parsed.path(), parsed.nextLevel());

            if (paramNames.isEmpty() && !parsed.path().isEmpty()) {
                response.setFault(true);
                response.setFaultCode(9005);
                response.setFaultString("Invalid parameter path: " + parsed.path());
                return response;
            }

            response.getParameters().put("ParameterNames", String.join(",", paramNames));
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
