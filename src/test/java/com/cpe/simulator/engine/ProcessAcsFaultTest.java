package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.cwmp.*;
import com.cpe.simulator.model.DataModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessAcsFaultTest {

    private CpeDevice device;
    private DataModel dataModel;

    @Mock
    private CwmpClient mockCwmpClient;

    @Mock
    private ScheduledExecutorService mockScheduler;

    private static final String INFORM_RESPONSE_BODY =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">" +
        "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">ACS-1</cwmp:ID></soap-env:Header>" +
        "<soap-env:Body><cwmp:InformResponse><MaxEnvelopes>1</MaxEnvelopes></cwmp:InformResponse></soap-env:Body>" +
        "</soap-env:Envelope>";

    private static final String ACS_GET_PARAM_VALUES_BODY =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:soap-enc=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:cwmp=\"urn:dslforum-org:cwmp-1-0\">" +
        "<soap-env:Header><cwmp:ID soap-env:mustUnderstand=\"1\">ACS-2</cwmp:ID></soap-env:Header>" +
        "<soap-env:Body><cwmp:GetParameterValues>" +
        "<ParameterNames soap-enc:arrayType=\"xsd:string[1]\">" +
        "<string>DeviceInfo.Manufacturer</string>" +
        "</ParameterNames></cwmp:GetParameterValues></soap-env:Body>" +
        "</soap-env:Envelope>";

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig();
        config.setCount(1);
        config.setSessionTimeout(30);
        config.setMaxSessions(10);

        dataModel = new DataModel();
        dataModel.saveDefaults();

        Semaphore semaphore = new Semaphore(10);

        device = new CpeDevice(
            "000000-SimCPE-SIM00001", "SIM00001", "000000", "SimCPE",
            dataModel, config, mockCwmpClient, mockScheduler, semaphore
        );
    }

    @Test
    void processAcsRequestsCatchesHandlerExceptionAndProducesFault9002() throws Exception {
        device.getRpcRegistry().register(new RpcHandler() {
            @Override
            public String methodName() { return "GetParameterValues"; }
            @Override
            public SoapEnvelope handle(DeviceContext device, SoapEnvelope request) {
                throw new RuntimeException("Test handler exception");
            }
        });

        CwmpClient.CwmpResponse informResponse = new CwmpClient.CwmpResponse(200, INFORM_RESPONSE_BODY, Map.of());
        CwmpClient.CwmpResponse acsRequest = new CwmpClient.CwmpResponse(200, ACS_GET_PARAM_VALUES_BODY, Map.of());
        CwmpClient.CwmpResponse endSession = new CwmpClient.CwmpResponse(204, null, Map.of());

        when(mockCwmpClient.sendInform(anyString(), anyString()))
            .thenReturn(informResponse);
        when(mockCwmpClient.sendRequest(anyString(), anyString()))
            .thenReturn(acsRequest)
            .thenReturn(endSession);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockScheduler).execute(any(Runnable.class));

        device.setState(CpeDevice.DeviceState.ONLINE);
        device.triggerInform("2 PERIODIC");

        assertEquals(CpeDevice.DeviceState.ONLINE, device.getState());
        assertEquals(CwmpSession.State.IDLE, device.getSession().getState());

        verify(mockCwmpClient, times(1)).sendInform(anyString(), anyString());

        ArgumentCaptor<String> soapXmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCwmpClient, times(2)).sendRequest(anyString(), soapXmlCaptor.capture());

        List<String> allXmls = soapXmlCaptor.getAllValues();
        String faultXml = allXmls.get(1);
        assertTrue(faultXml.contains("<FaultCode>9002</FaultCode>"), "Expected FaultCode 9002 in response");
        assertTrue(faultXml.contains("Internal error"), "Expected 'Internal error' fault string");
    }
}