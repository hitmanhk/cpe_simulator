package com.cpe.simulator.cwmp;

public class GetOptionsHandler implements RpcHandler {

    @Override
    public String methodName() {
        return "GetOptions";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetOptionsResponse");
        return response;
    }
}
