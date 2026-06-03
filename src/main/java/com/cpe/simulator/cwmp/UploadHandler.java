package com.cpe.simulator.cwmp;

import java.time.Instant;

public class UploadHandler implements RpcHandler {

    @Override
    public String methodName() {
        return "Upload";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("UploadResponse");

        String startTime = Instant.now().toString();
        String completeTime = Instant.now().toString();

        response.getParameters().put("Status", "0");
        response.getParameters().put("StartTime", startTime);
        response.getParameters().put("CompleteTime", completeTime);

        int delaySeconds = device.getConfig().getDownloadDelay();

        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
                String transferCompleteTime = Instant.now().toString();
                CwmpSession.PendingTransfer transfer = new CwmpSession.PendingTransfer(
                    "", "Upload", 0, startTime, transferCompleteTime);
                device.addPendingTransfer(transfer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return response;
    }
}
