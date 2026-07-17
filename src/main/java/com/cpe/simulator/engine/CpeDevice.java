package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.IdGenerator;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.cwmp.*;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import com.cpe.simulator.xmpp.XmppConnection;
import com.cpe.simulator.xmpp.XmppEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CpeDevice implements DeviceContext {

    public enum DeviceState { CREATED, CONNECTING, ONLINE, OFFLINE }

    private static final String XMPP_PASSWORD_TR181 = "Device.XMPP.Connection.1.Password";
    private static final String XMPP_PASSWORD_TR098 = "InternetGatewayDevice.XMPP.Connection.1.Password";
    private static final String XMPP_USERNAME_TR181 = "Device.XMPP.Connection.1.Username";
    private static final String XMPP_USERNAME_TR098 = "InternetGatewayDevice.XMPP.Connection.1.Username";

    private final String deviceId;
    private final String serialNumber;
    private final String oui;
    private final String productClass;
    private final DataModel dataModel;
    private final CwmpSession session;
    private volatile XmppConnection xmppConnection;
    private volatile XmppEngine xmppEngine;
    private final AppConfig config;
    private final RpcRegistry rpcRegistry;
    private final CwmpClient cwmpClient;
    private volatile DeviceState state;
    private volatile Instant lastInformTime;
    private final AtomicLong informCount;
    private final AtomicBoolean sessionActive;
    private final ConcurrentLinkedQueue<PendingInform> pendingInforms;
    private final List<CwmpSession.PendingTransfer> pendingTransfers;
    private final ScheduledExecutorService scheduler;
    private final Semaphore sessionSemaphore;
    private final InformHandler informHandler;
    private final SoapBuilder soapBuilder;
    private final SoapParser soapParser;

    public CpeDevice(String deviceId, String serialNumber, String oui, String productClass,
                     DataModel dataModel, AppConfig config, CwmpClient cwmpClient,
                     ScheduledExecutorService scheduler, Semaphore sessionSemaphore) {
        this.deviceId = deviceId;
        this.serialNumber = serialNumber;
        this.oui = oui;
        this.productClass = productClass;
        this.dataModel = dataModel;
        this.config = config;
        this.cwmpClient = cwmpClient;
        this.scheduler = scheduler;
        this.sessionSemaphore = sessionSemaphore;
        this.session = new CwmpSession(config.getSessionTimeout());
        this.rpcRegistry = new RpcRegistry(this);
        this.state = DeviceState.CREATED;
        this.informCount = new AtomicLong(0);
        this.sessionActive = new AtomicBoolean(false);
        this.pendingInforms = new ConcurrentLinkedQueue<>();
        this.pendingTransfers = new ArrayList<>();
        this.informHandler = new InformHandler();
        this.soapBuilder = new SoapBuilder();
        this.soapParser = new SoapParser();
    }

    public void initialize() {
        transitionState(DeviceState.CREATED, "device initialized");
    }

    public void connect() {
        transitionState(DeviceState.CONNECTING, "connection requested");
        if (!config.isNatMode()) {
            transitionState(DeviceState.ONLINE, "public mode ready");
        }
    }

    public void disconnect() {
        if (xmppConnection != null) {
            xmppConnection.disconnect();
        }
        transitionState(DeviceState.OFFLINE, "disconnect requested");
        pendingInforms.clear();
    }

    public void triggerInform(String eventCode) {
        triggerInform(eventCode, "");
    }

    public void triggerInform(String eventCode, String commandKey) {
        if (state == DeviceState.OFFLINE) {
            SimLogger.device(deviceId, "Cannot trigger inform, device state: {}", state);
            return;
        }
        pendingInforms.add(new PendingInform(eventCode, commandKey != null ? commandKey : ""));
        startInformDrain();
    }

    private void startInformDrain() {
        if (!sessionActive.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.execute(this::drainPendingInforms);
        } catch (RuntimeException e) {
            sessionActive.set(false);
            throw e;
        }
    }

    private void drainPendingInforms() {
        try {
            PendingInform pending;
            while (state != DeviceState.OFFLINE && (pending = pendingInforms.poll()) != null) {
                runInformSession(pending.eventCode(), pending.commandKey());
            }
            if (state == DeviceState.OFFLINE) {
                pendingInforms.clear();
            }
        } catch (Exception e) {
            SimLogger.error("Inform session failed for device " + deviceId, e);
        } finally {
            sessionActive.set(false);
            if (state != DeviceState.OFFLINE && !pendingInforms.isEmpty()) {
                startInformDrain();
            }
        }
    }

    private record PendingInform(String eventCode, String commandKey) {}

    public void runInformSession() {
        runInformSession("2 PERIODIC", "");
    }

    private void runInformSession(String eventCode, String commandKey) {
        boolean acquired = false;
        String sessionId = null;
        try {
            acquired = sessionSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                SimLogger.device(deviceId, "Failed to acquire session semaphore");
                return;
            }

            List<SoapEnvelope.EventStruct> events = new ArrayList<>();
            events.add(new SoapEnvelope.EventStruct(eventCode, commandKey != null ? commandKey : ""));

            session.startSession(events);
            sessionId = session.getSessionId();
            String activeSessionId = sessionId;

            String informXml = informHandler.buildInform(this, eventCode, commandKey);

            CwmpClient.CwmpResponse response = sendCwmpRpc(
                activeSessionId, "Inform", "eventCode=" + eventCode,
                () -> cwmpClient.sendInform(activeSessionId, informXml));

            if (response.statusCode() == 401) {
                session.close();
                return;
            }

            if (response.statusCode() != 200) {
                session.close();
                return;
            }

            boolean holdRequests;
            try {
                holdRequests = soapParser.parseInformResponse(response.body());
                SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", "InformResponse",
                    "RECEIVED", "holdRequests=" + holdRequests);
            } catch (Exception e) {
                SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", "InformResponse",
                    "PARSE_FAILED", "error=" + e.getClass().getSimpleName());
                throw e;
            }

            lastInformTime = Instant.now();
            informCount.incrementAndGet();

            if (holdRequests) {
                processAcsRequests(sessionId);
            } else {
                sendEmptyRequest(sessionId);
            }

            session.close();
            SimLogger.device(deviceId, "Inform session complete");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SimLogger.device(deviceId, "Inform session interrupted");
            session.close();
        } catch (Exception e) {
            SimLogger.error("Inform session error for device " + deviceId, e);
            session.close();
        } finally {
            if (acquired) {
                sessionSemaphore.release();
            }
            cwmpClient.closeSession(sessionId);
        }
    }

    private void processAcsRequests(String sessionId) throws Exception {
        while (session.getState() == CwmpSession.State.IN_SESSION) {
            session.touch();

            SoapEnvelope request;
            String methodName;

            SoapEnvelope pendingRequest = session.getLastResponse();
            if (pendingRequest != null) {
                session.setLastResponse(null);
                request = pendingRequest;
                methodName = pendingRequest.getMethodName();
            } else {
                CwmpClient.CwmpResponse acsResponse = sendCwmpRpc(
                    sessionId, "EmptyRequest", "requesting ACS RPC",
                    () -> cwmpClient.sendRequest(sessionId, ""));

                if (acsResponse.statusCode() == 204) {
                    return;
                }

                if (acsResponse.statusCode() != 200 || acsResponse.body() == null || acsResponse.body().isEmpty()) {
                    break;
                }

                methodName = soapParser.extractMethodName(acsResponse.body());
                if (methodName == null) {
                    break;
                }

                request = soapParser.parse(acsResponse.body());
            }

            SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", methodName,
                "RECEIVED", "cwmpId=" + logValue(request.getCwmpId()));

            RpcHandler handler = rpcRegistry.getHandler(methodName);

            String responseXml;
            String responseMethod;
            String responseDetails;
            if (handler != null) {
                try {
                    SoapEnvelope responseEnvelope = handler.handle(this, request);
                    responseXml = soapBuilder.buildResponse(responseEnvelope);
                    responseMethod = responseEnvelope.getMethodName() != null
                        ? responseEnvelope.getMethodName() : methodName + "Response";
                    responseDetails = "requestMethod=" + methodName;
                    SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", methodName,
                        "HANDLED", "responseMethod=" + responseMethod);
                } catch (Exception e) {
                    SimLogger.error("RPC handler error for " + methodName + " on device " + deviceId, e);
                    String faultCwmpId = request.getCwmpId() != null ? request.getCwmpId() : IdGenerator.cwmpId();
                    responseXml = soapBuilder.buildFault(9002, "Internal error: " + e.getMessage(), faultCwmpId);
                    responseMethod = "Fault";
                    responseDetails = "requestMethod=" + methodName + " faultCode=9002";
                    SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", methodName,
                        "HANDLER_FAILED", "faultCode=9002 error=" + e.getClass().getSimpleName());
                }
            } else {
                String faultCwmpId = request.getCwmpId() != null ? request.getCwmpId() : IdGenerator.cwmpId();
                responseXml = soapBuilder.buildFault(9000, "Method not supported: " + methodName, faultCwmpId);
                responseMethod = "Fault";
                responseDetails = "requestMethod=" + methodName + " faultCode=9000";
                SimLogger.cwmpRpc(deviceId, sessionId, "ACS_TO_CPE", methodName,
                    "UNSUPPORTED", "faultCode=9000");
            }

            String outboundResponseXml = responseXml;
            CwmpClient.CwmpResponse nextResponse = sendCwmpRpc(
                sessionId, responseMethod, responseDetails,
                () -> cwmpClient.sendRequest(sessionId, outboundResponseXml));
            session.touch();

            if (nextResponse.statusCode() == 204) {
                return;
            }

            if (nextResponse.statusCode() == 200 && nextResponse.body() != null && !nextResponse.body().isEmpty()) {
                String nextMethod = soapParser.extractMethodName(nextResponse.body());
                if (nextMethod != null) {
                    session.setLastResponse(soapParser.parse(nextResponse.body()));
                    continue;
                }
            }
            break;
        }
    }

    private void sendEmptyRequest(String sessionId) throws Exception {
        session.touch();
        String emptyXml = soapBuilder.buildEmptyRequest(IdGenerator.cwmpId());
        CwmpClient.CwmpResponse response = sendCwmpRpc(
            sessionId, "EmptyRequest", "requesting ACS RPC",
            () -> cwmpClient.sendRequest(sessionId, emptyXml));

        if (response.statusCode() == 204) {
            return;
        }

        if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
            String methodName = soapParser.extractMethodName(response.body());
            if (methodName != null) {
                session.setLastResponse(soapParser.parse(response.body()));
                session.touch();
                processAcsRequests(sessionId);
            }
        }
    }

    private CwmpClient.CwmpResponse sendCwmpRpc(String sessionId, String method,
                                                 String details, CwmpCall call) throws Exception {
        try {
            CwmpClient.CwmpResponse response = call.send();
            String status = switch (response.statusCode()) {
                case 200, 204 -> "SUCCESS";
                case 401 -> "AUTH_FAILED";
                default -> response.statusCode() >= 400 ? "HTTP_ERROR" : "UNEXPECTED_HTTP_STATUS";
            };
            SimLogger.cwmpRpc(deviceId, sessionId, "CPE_TO_ACS", method, status,
                appendDetail(details, "httpStatus=" + response.statusCode()));
            return response;
        } catch (Exception e) {
            SimLogger.cwmpRpc(deviceId, sessionId, "CPE_TO_ACS", method, "SEND_FAILED",
                appendDetail(details, "error=" + e.getClass().getSimpleName()));
            throw e;
        }
    }

    private static String appendDetail(String details, String additionalDetail) {
        return details == null || details.isBlank()
            ? additionalDetail : details + " " + additionalDetail;
    }

    private static String logValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @FunctionalInterface
    private interface CwmpCall {
        CwmpClient.CwmpResponse send() throws Exception;
    }

    public void scheduleReboot(int delaySeconds) {
        scheduler.schedule(() -> {
            SimLogger.device(deviceId, "Simulating reboot");
            transitionState(DeviceState.OFFLINE, "reboot started");
            session.close();
            try {
                Thread.sleep(config.getRebootDelay() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            dataModel.factoryReset();
            transitionState(DeviceState.ONLINE, "reboot completed");
            triggerInform("1 BOOT", "");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void scheduleFactoryReset(int delaySeconds) {
        scheduler.schedule(() -> {
            SimLogger.device(deviceId, "Simulating factory reset");
            transitionState(DeviceState.OFFLINE, "factory reset started");
            session.close();
            dataModel.factoryReset();
            transitionState(DeviceState.ONLINE, "factory reset completed");
            triggerInform("1 BOOT", "");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    @Override
    public DataModel getDataModel() {
        return dataModel;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getSerialNumber() {
        return serialNumber;
    }

    @Override
    public String getOui() {
        return oui;
    }

    @Override
    public String getProductClass() {
        return productClass;
    }

    @Override
    public AppConfig getConfig() {
        return config;
    }

    @Override
    public CwmpSession getSession() {
        return session;
    }

    @Override
    public void addPendingTransfer(CwmpSession.PendingTransfer transfer) {
        synchronized (pendingTransfers) {
            pendingTransfers.add(transfer);
        }
    }

    @Override
    public List<CwmpSession.PendingTransfer> getPendingTransfers() {
        synchronized (pendingTransfers) {
            return new ArrayList<>(pendingTransfers);
        }
    }

    @Override
    public void clearPendingTransfers() {
        synchronized (pendingTransfers) {
            pendingTransfers.clear();
        }
    }

    public DeviceState getState() {
        return state;
    }

    public void setState(DeviceState state) {
        transitionState(state, "state updated");
    }

    void transitionState(DeviceState newState, String reason) {
        Objects.requireNonNull(newState, "newState");
        DeviceState previousState;
        synchronized (this) {
            previousState = this.state;
            if (previousState == newState) {
                return;
            }
            this.state = newState;
        }
        SimLogger.connectionState(deviceId, "DEVICE", previousState, newState, reason);
    }

    public long getInformCount() {
        return informCount.get();
    }

    public Instant getLastInformTime() {
        return lastInformTime;
    }

    public XmppConnection getXmppConnection() {
        return xmppConnection;
    }

    public void setXmppConnection(XmppConnection xmppConnection) {
        this.xmppConnection = xmppConnection;
    }

    public XmppEngine getXmppEngine() {
        return xmppEngine;
    }

    public void setXmppEngine(XmppEngine xmppEngine) {
        this.xmppEngine = xmppEngine;
    }

    @Override
    public void connectXmpp() {
        if (xmppConnection != null || xmppEngine == null) {
            return;
        }

        String password = null;
        ParameterNode node181 = dataModel.getNode(XMPP_PASSWORD_TR181);
        if (node181 != null && node181.getValue() != null && !node181.getValue().getValue().isEmpty()) {
            password = node181.getValue().getValue();
        } else {
            ParameterNode node098 = dataModel.getNode(XMPP_PASSWORD_TR098);
            if (node098 != null && node098.getValue() != null && !node098.getValue().getValue().isEmpty()) {
                password = node098.getValue().getValue();
            }
        }

        if (password == null) {
            return;
        }

        String username = getNonEmptyParameter(XMPP_USERNAME_TR181);
        if (username == null) {
            username = getNonEmptyParameter(XMPP_USERNAME_TR098);
        }

        XmppConnection conn = xmppEngine.createConnection(deviceId, serialNumber);
        if (username != null) {
            conn.setXmppUsername(username);
        }
        conn.setXmppPassword(password);
        conn.onStateChange(xmppState -> {
            switch (xmppState) {
                case CONNECTING, TLS_NEGOTIATING, AUTHENTICATING, AUTHENTICATED, BINDING, BOUND ->
                    transitionState(DeviceState.CONNECTING, "XMPP state=" + xmppState);
                case ONLINE -> transitionState(DeviceState.ONLINE, "XMPP connected");
                case FAILED -> {
                    transitionState(DeviceState.OFFLINE, "XMPP connection failed");
                    SimLogger.error("XMPP connection failed for device {}", deviceId);
                }
                case DISCONNECTED -> transitionState(DeviceState.OFFLINE, "XMPP disconnected");
            }
        });
        xmppConnection = conn;
        xmppEngine.addConnection(conn);
    }

    private String getNonEmptyParameter(String path) {
        ParameterNode node = dataModel.getNode(path);
        if (node == null || node.getValue() == null || node.getValue().getValue().isEmpty()) {
            return null;
        }
        return node.getValue().getValue();
    }

    public RpcRegistry getRpcRegistry() {
        return rpcRegistry;
    }

    public CwmpClient getCwmpClient() {
        return cwmpClient;
    }

    public boolean isSessionActive() {
        return sessionActive.get();
    }
}
