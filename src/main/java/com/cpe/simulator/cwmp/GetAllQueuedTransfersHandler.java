package com.cpe.simulator.cwmp;

import java.util.List;

public class GetAllQueuedTransfersHandler implements RpcHandler {

    @Override
    public String methodName() {
        return "GetAllQueuedTransfers";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetAllQueuedTransfersResponse");

        List<CwmpSession.PendingTransfer> transfers = device.getPendingTransfers();
        response.getParameters().put("TransferCount", String.valueOf(transfers.size()));

        return response;
    }
}
