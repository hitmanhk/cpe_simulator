package com.cpe.simulator.cwmp;

public class ScheduleInformHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "ScheduleInform";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("ScheduleInformResponse");

        try {
            SoapParser.ScheduleInformRequest parsed = soapParser.parseScheduleInform(request.getRawBody());
            int delaySeconds = parsed.delaySeconds();

            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                    device.triggerInform("M ScheduleInform");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
