package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterValue;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DownloadHandler implements RpcHandler {

    private final SoapParser soapParser = new SoapParser();

    @Override
    public String methodName() {
        return "Download";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("DownloadResponse");

        try {
            SoapParser.DownloadRequest parsed = soapParser.parseDownload(request.getRawBody());
            String startTime = Instant.now().toString();
            String completeTime = Instant.now().toString();

            response.getParameters().put("Status", "0");
            response.getParameters().put("StartTime", startTime);
            response.getParameters().put("CompleteTime", completeTime);

            int delaySeconds = device.getConfig().getDownloadDelay();
            double successRate = device.getConfig().getDownloadSuccessRate();
            String commandKey = parsed.commandKey();
            String fileType = parsed.fileType();

            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                    int faultCode = Math.random() < successRate ? 0 : 9010;
                    String transferCompleteTime = Instant.now().toString();

                    if (faultCode == 0 && "1 Firmware Upgrade Image".equals(fileType)) {
                        DataModel dm = device.getDataModel();
                        ParameterValue swVersion = dm.getValue("Device.DeviceInfo.SoftwareVersion");
                        if (swVersion != null) {
                            swVersion.setValue("UPGRADED-" + System.currentTimeMillis());
                        }
                    }

                    CwmpSession.PendingTransfer transfer = new CwmpSession.PendingTransfer(
                        commandKey, fileType, faultCode, startTime, transferCompleteTime);
                    device.addPendingTransfer(transfer);
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
