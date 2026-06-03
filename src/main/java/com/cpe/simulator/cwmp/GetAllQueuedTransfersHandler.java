package com.cpe.simulator.cwmp;

import java.util.ArrayList;
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
        List<SoapBuilder.QueuedTransfer> queuedTransfers = new ArrayList<>();
        for (CwmpSession.PendingTransfer t : transfers) {
            int state = t.faultCode() == 0 ? 1 : 4;
            queuedTransfers.add(new SoapBuilder.QueuedTransfer(t.commandKey(), t.fileType(), state, 0));
        }
        response.setTransferList(queuedTransfers);

        return response;
    }
}
