package com.cpe.simulator.cwmp;

import java.util.List;

public class GetRPCMethodsHandler implements RpcHandler {

    private final RpcRegistry registry;

    public GetRPCMethodsHandler(RpcRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String methodName() {
        return "GetRPCMethods";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("GetRPCMethodsResponse");

        List<String> methods = registry.getRegisteredMethods();
        response.setStringList(methods);

        return response;
    }
}
