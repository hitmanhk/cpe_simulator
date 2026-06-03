package com.cpe.simulator.cwmp;

public class FactoryResetHandler implements RpcHandler {

    @Override
    public String methodName() {
        return "FactoryReset";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("FactoryResetResponse");

        device.scheduleFactoryReset(device.getConfig().getRebootDelay());

        return response;
    }
}
