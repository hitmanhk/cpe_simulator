package com.cpe.simulator.cwmp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RpcRegistry {

    private final Map<String, RpcHandler> handlers = new LinkedHashMap<>();

    public RpcRegistry(DeviceContext device) {
        register(new GetParameterValuesHandler());
        register(new SetParameterValuesHandler());
        register(new GetParameterNamesHandler());
        register(new GetParameterAttributesHandler());
        register(new SetParameterAttributesHandler());
        register(new AddObjectHandler());
        register(new DeleteObjectHandler());
        register(new DownloadHandler());
        register(new RebootHandler());
        register(new FactoryResetHandler());
        register(new ScheduleInformHandler());
        register(new GetQueuedTransfersHandler());
        register(new GetAllQueuedTransfersHandler());
        register(new GetRPCMethodsHandler(this));
        register(new GetOptionsHandler());
        register(new SetVouchersHandler());
        register(new UploadHandler());
    }

    public void register(RpcHandler handler) {
        handlers.put(handler.methodName(), handler);
    }

    public RpcHandler getHandler(String methodName) {
        return handlers.get(methodName);
    }

    public List<String> getRegisteredMethods() {
        return new ArrayList<>(handlers.keySet());
    }
}
