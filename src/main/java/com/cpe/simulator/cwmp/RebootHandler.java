package com.cpe.simulator.cwmp;

public class RebootHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "Reboot";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("RebootResponse");

        try {
            SoapParser.RebootRequest parsed = soapParser.parseReboot(request.getRawBody());
            device.scheduleReboot(device.getConfig().getRebootDelay());
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
