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
        this.state = DeviceState.CREATED;
    }

    public void connect() {
        this.state = DeviceState.CONNECTING;
        if (!config.isNatMode()) {
            this.state = DeviceState.ONLINE;
            SimLogger.device(deviceId, "Device online (public mode)");
        }
    }

    public void disconnect() {
        if (xmppConnection != null) {
            xmppConnection.disconnect();
        }
        this.state = DeviceState.OFFLINE;
        pendingInforms.clear();
        SimLogger.device(deviceId, "Device disconnected");
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

            String informXml = informHandler.buildInform(this, eventCode, commandKey);

            CwmpClient.CwmpResponse response = cwmpClient.sendInform(sessionId, informXml);

            if (response.statusCode() == 401) {
                SimLogger.device(deviceId, "CWMP authentication failed for device");
                session.close();
                return;
            }

            if (response.statusCode() != 200) {
                SimLogger.device(deviceId, "Inform failed with status {}", response.statusCode());
                session.close();
                return;
            }

            boolean holdRequests = soapParser.parseInformResponse(response.body());

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
                CwmpClient.CwmpResponse acsResponse = cwmpClient.sendRequest(sessionId, "");

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

            RpcHandler handler = rpcRegistry.getHandler(methodName);

            String responseXml;
            if (handler != null) {
                try {
                    SoapEnvelope responseEnvelope = handler.handle(this, request);
                    responseXml = soapBuilder.buildResponse(responseEnvelope);
                } catch (Exception e) {
                    SimLogger.error("RPC handler error for " + methodName + " on device " + deviceId, e);
                    String faultCwmpId = request.getCwmpId() != null ? request.getCwmpId() : IdGenerator.cwmpId();
                    responseXml = soapBuilder.buildFault(9002, "Internal error: " + e.getMessage(), faultCwmpId);
                }
            } else {
                String faultCwmpId = request.getCwmpId() != null ? request.getCwmpId() : IdGenerator.cwmpId();
                String faultXml = soapBuilder.buildFault(9000, "Method not supported: " + methodName, faultCwmpId);
                responseXml = faultXml;
            }

            CwmpClient.CwmpResponse nextResponse = cwmpClient.sendRequest(sessionId, responseXml);
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
        CwmpClient.CwmpResponse response = cwmpClient.sendRequest(sessionId, emptyXml);

        if (response.statusCode() == 204) {
            return;
        }

        if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
            String methodName = soapParser.extractMethodName(response.body());
            if (methodName != null) {
                session.touch();
                processAcsRequests(sessionId);
            }
        }
    }

    public void scheduleReboot(int delaySeconds) {
        scheduler.schedule(() -> {
            SimLogger.device(deviceId, "Simulating reboot");
            this.state = DeviceState.OFFLINE;
            session.close();
            try {
                Thread.sleep(config.getRebootDelay() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            dataModel.factoryReset();
            this.state = DeviceState.ONLINE;
            triggerInform("1 BOOT", "");
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void scheduleFactoryReset(int delaySeconds) {
        scheduler.schedule(() -> {
            SimLogger.device(deviceId, "Simulating factory reset");
            this.state = DeviceState.OFFLINE;
            session.close();
            dataModel.factoryReset();
            this.state = DeviceState.ONLINE;
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
        this.state = state;
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
                case ONLINE -> {
                    state = DeviceState.ONLINE;
                    SimLogger.device(deviceId, "XMPP connected, device online");
                }
                case FAILED -> {
                    state = DeviceState.OFFLINE;
                    SimLogger.error("XMPP connection failed for device {}", deviceId);
                }
                case DISCONNECTED -> state = DeviceState.OFFLINE;
                default -> {}
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
