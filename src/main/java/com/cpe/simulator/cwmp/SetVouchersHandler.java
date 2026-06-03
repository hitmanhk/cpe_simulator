package com.cpe.simulator.cwmp;

public class SetVouchersHandler implements RpcHandler {

    @Override
    public String methodName() {
        return "SetVouchers";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("SetVouchersResponse");
        return response;
    }
}
