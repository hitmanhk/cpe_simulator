package com.cpe.simulator.cwmp;

public interface RpcHandler {
    String methodName();
    SoapEnvelope handle(DeviceContext device, SoapEnvelope request);
}
