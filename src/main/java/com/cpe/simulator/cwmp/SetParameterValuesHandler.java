package com.cpe.simulator.cwmp;

import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.model.ParameterValue;

import java.util.List;

public class SetParameterValuesHandler implements RpcHandler {

    private static final String XMPP_PASSWORD_TR181 = "Device.XMPP.Connection.1.Password";
    private static final String XMPP_PASSWORD_TR098 = "InternetGatewayDevice.XMPP.Connection.1.Password";

    private final SoapParser soapParser = new SoapParser();
    private final SoapBuilder soapBuilder = new SoapBuilder();

    @Override
    public String methodName() {
        return "SetParameterValues";
    }

    @Override
    public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
        SoapEnvelope response = new SoapEnvelope();
        response.setCwmpId(request.getCwmpId());
        response.setMethodName("SetParameterValuesResponse");

        try {
            List<SoapEnvelope.ParameterInfo> params = soapParser.parseSetParameterValues(request.getRawBody());
            DataModel dm = device.getDataModel();

            for (SoapEnvelope.ParameterInfo param : params) {
                ParameterNode node = dm.getNode(param.name());
                if (node == null) {
                    response.setFault(true);
                    response.setFaultCode(9005);
                    response.setFaultString("Invalid parameter name: " + param.name());
                    return response;
                }

                if (!node.isWritable()) {
                    response.setFault(true);
                    response.setFaultCode(9008);
                    response.setFaultString("Attempt to set read-only parameter: " + param.name());
                    return response;
                }

                ParameterValue existingValue = node.getValue();
                if (existingValue != null) {
                    String targetType = param.type() != null ? param.type() : existingValue.getType();
                    if (!ParameterValue.validateType(param.value(), targetType)) {
                        response.setFault(true);
                        response.setFaultCode(9007);
                        response.setFaultString("Type mismatch for parameter: " + param.name());
                        return response;
                    }
                }
            }

            boolean valueChangeDetected = false;
            for (SoapEnvelope.ParameterInfo param : params) {
                ParameterNode node = dm.getNode(param.name());
                ParameterValue existingValue = node.getValue();
                String oldValue = existingValue != null ? existingValue.getValue() : null;

                if (existingValue != null) {
                    existingValue.setValue(param.value());
                    if (param.type() != null) {
                        existingValue.setType(param.type());
                    }
                }

                if (node.getNotification() == 2 && existingValue != null
                    && oldValue != null && !oldValue.equals(param.value())) {
                    valueChangeDetected = true;
                }
            }

            if (valueChangeDetected) {
                device.triggerInform("4 VALUE CHANGE");
            }

            boolean xmppPasswordSet = false;
            for (SoapEnvelope.ParameterInfo param : params) {
                if (XMPP_PASSWORD_TR181.equals(param.name()) || XMPP_PASSWORD_TR098.equals(param.name())) {
                    xmppPasswordSet = true;
                    break;
                }
            }

            if (xmppPasswordSet) {
                device.connectXmpp();
            }

            response.getParameters().put("Status", "0");
        } catch (Exception e) {
            response.setFault(true);
            response.setFaultCode(9003);
            response.setFaultString(e.getMessage());
        }

        return response;
    }
}
