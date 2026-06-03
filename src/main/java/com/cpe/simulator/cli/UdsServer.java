package com.cpe.simulator.cli;

import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.DeviceManager;
import com.cpe.simulator.engine.SimulationEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdsServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER_SIZE = 1024 * 1024;

    private final SimulationEngine engine;
    private final String socketPath;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private ServerSocketChannel serverChannel;

    public UdsServer(SimulationEngine engine) {
        this(engine, UdsClient.getDefaultSocketPath());
    }

    public UdsServer(SimulationEngine engine, String socketPath) {
        this.engine = engine;
        this.socketPath = socketPath;
    }

    public void start() {
        running.set(true);
        Thread.ofVirtual().name("uds-server").start(this::run);
    }

    public void stop() {
        running.set(false);
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (Exception e) {
                SimLogger.error("Error closing UDS server", e);
            }
        }
        Path sp = Path.of(socketPath);
        try {
            Files.deleteIfExists(sp);
        } catch (Exception e) {
            SimLogger.error("Error deleting socket file", e);
        }
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    private void run() {
        Path sp = Path.of(socketPath);
        try {
            Files.deleteIfExists(sp);
            SocketAddress address = UnixDomainSocketAddress.of(sp);
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(address);
            SimLogger.info("UDS management server started at {}", socketPath);

            while (running.get()) {
                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    if (clientChannel != null) {
                        Thread.ofVirtual().name("uds-handler").start(() -> handleClient(clientChannel));
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        SimLogger.error("Error accepting UDS connection", e);
                    }
                }
            }
        } catch (Exception e) {
            SimLogger.error("UDS server error", e);
        }
    }

    private void handleClient(SocketChannel channel) {
        try (channel) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            StringBuilder requestBuilder = new StringBuilder();
            int bytesRead;
            while ((bytesRead = channel.read(buffer)) > 0) {
                buffer.flip();
                byte[] bytes = new byte[bytesRead];
                buffer.get(bytes);
                requestBuilder.append(new String(bytes, StandardCharsets.UTF_8));
                buffer.clear();
            }

            String requestStr = requestBuilder.toString();
            if (requestStr.isEmpty()) {
                return;
            }

            JsonNode request = MAPPER.readTree(requestStr);
            String action = request.has("action") ? request.get("action").asText() : "";
            JsonNode params = request.has("params") ? request.get("params") : null;

            JsonNode response = processAction(action, params);
            byte[] responseBytes = MAPPER.writeValueAsBytes(response);
            channel.write(ByteBuffer.wrap(responseBytes));
        } catch (Exception e) {
            SimLogger.error("Error handling UDS client", e);
        }
    }

    private JsonNode processAction(String action, JsonNode params) {
        ObjectNode response = MAPPER.createObjectNode();
        try {
            switch (action) {
                case "stop" -> {
                    boolean force = params != null && params.has("force") && params.get("force").asBoolean();
                    int timeout = params != null && params.has("timeout") ? params.get("timeout").asInt(30) : 30;
                    response.put("status", "ok");
                    response.put("message", force ? "Force stopping" : "Stopping gracefully");
                    shutdownRequested.set(true);
                }
                case "status" -> {
                    var status = engine.getStatus();
                    response.put("status", "ok");
                    response.put("running", status.running());
                    response.put("mode", status.mode());
                    response.put("totalDevices", status.totalDevices());
                    response.put("onlineDevices", status.onlineDevices());
                    response.put("activeSessions", status.activeSessions());
                    response.put("totalInforms", status.totalInforms());
                    response.put("uptimeSeconds", status.uptime().getSeconds());
                    response.put("uptimeFormatted", formatDuration(status.uptime()));
                    response.put("jvmMemoryUsed", status.jvmMemoryUsed());
                    response.put("jvmMemoryMax", status.jvmMemoryMax());
                    response.put("threadCount", status.threadCount());
                }
                case "device-list" -> {
                    boolean onlineOnly = params != null && params.has("online") && params.get("online").asBoolean();
                    int limit = params != null && params.has("limit") ? params.get("limit").asInt(100) : 100;
                    ArrayNode devices = listDevices(onlineOnly, limit);
                    response.put("status", "ok");
                    response.set("devices", devices);
                    response.put("count", devices.size());
                }
                case "device-get" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    String paramPath = params != null && params.has("path") ? params.get("path").asText() : null;
                    if (deviceId == null) {
                        response.put("status", "error");
                        response.put("error", "deviceId required");
                    } else {
                        ObjectNode deviceInfo = getDeviceInfo(deviceId, paramPath);
                        if (deviceInfo != null) {
                            response.put("status", "ok");
                            response.set("device", deviceInfo);
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device not found: " + deviceId);
                        }
                    }
                }
                case "device-add" -> {
                    int count = params != null && params.has("count") ? params.get("count").asInt(1) : 1;
                    DeviceManager dm = engine.getDeviceManager();
                    if (dm != null) {
                        dm.addDevices(count);
                        response.put("status", "ok");
                        response.put("message", "Added " + count + " device(s)");
                    } else {
                        response.put("status", "error");
                        response.put("error", "Device manager not available");
                    }
                }
                case "device-remove" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    if (deviceId == null) {
                        response.put("status", "error");
                        response.put("error", "deviceId required");
                    } else {
                        DeviceManager dm = engine.getDeviceManager();
                        if (dm != null) {
                            dm.removeDevice(deviceId);
                            response.put("status", "ok");
                            response.put("message", "Removed device: " + deviceId);
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device manager not available");
                        }
                    }
                }
                case "device-inform" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    int randomCount = params != null && params.has("random") ? params.get("random").asInt(0) : 0;
                    if (randomCount > 0) {
                        triggerRandomInforms(randomCount);
                        response.put("status", "ok");
                        response.put("message", "Triggered " + randomCount + " random inform(s)");
                    } else if (deviceId != null) {
                        CpeDevice device = findDevice(deviceId);
                        if (device != null) {
                            device.triggerInform("2 PERIODIC");
                            response.put("status", "ok");
                            response.put("message", "Triggered inform for: " + deviceId);
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device not found: " + deviceId);
                        }
                    } else {
                        response.put("status", "error");
                        response.put("error", "deviceId or random count required");
                    }
                }
                case "event-reboot" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    if (deviceId == null) {
                        response.put("status", "error");
                        response.put("error", "deviceId required");
                    } else {
                        CpeDevice device = findDevice(deviceId);
                        if (device != null) {
                            device.scheduleReboot(0);
                            response.put("status", "ok");
                            response.put("message", "Reboot scheduled for: " + deviceId);
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device not found: " + deviceId);
                        }
                    }
                }
                case "event-factory-reset" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    if (deviceId == null) {
                        response.put("status", "error");
                        response.put("error", "deviceId required");
                    } else {
                        CpeDevice device = findDevice(deviceId);
                        if (device != null) {
                            device.scheduleFactoryReset(0);
                            response.put("status", "ok");
                            response.put("message", "Factory reset scheduled for: " + deviceId);
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device not found: " + deviceId);
                        }
                    }
                }
                case "event-value-change" -> {
                    String deviceId = params != null && params.has("deviceId") ? params.get("deviceId").asText() : null;
                    String paramPath = params != null && params.has("param") ? params.get("param").asText() : null;
                    String value = params != null && params.has("value") ? params.get("value").asText() : null;
                    if (deviceId == null || paramPath == null || value == null) {
                        response.put("status", "error");
                        response.put("error", "deviceId, param, and value required");
                    } else {
                        CpeDevice device = findDevice(deviceId);
                        if (device != null) {
                            device.getDataModel().setValue(paramPath, value);
                            device.triggerInform("4 VALUE CHANGE");
                            response.put("status", "ok");
                            response.put("message", "Value changed and inform triggered");
                        } else {
                            response.put("status", "error");
                            response.put("error", "Device not found: " + deviceId);
                        }
                    }
                }
                default -> {
                    response.put("status", "error");
                    response.put("error", "Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return response;
    }

    private ArrayNode listDevices(boolean onlineOnly, int limit) {
        ArrayNode devices = MAPPER.createArrayNode();
        DeviceManager dm = engine.getDeviceManager();
        if (dm == null) {
            return devices;
        }
        List<CpeDevice> allDevices = dm.getDevices();
        int count = 0;
        for (CpeDevice device : allDevices) {
            if (count >= limit) break;
            if (onlineOnly && device.getState() != CpeDevice.DeviceState.ONLINE) continue;
            ObjectNode node = MAPPER.createObjectNode();
            node.put("deviceId", device.getDeviceId());
            node.put("serial", device.getSerialNumber());
            node.put("oui", device.getOui());
            node.put("productClass", device.getProductClass());
            node.put("state", device.getState().name());
            node.put("informCount", device.getInformCount());
            if (device.getLastInformTime() != null) {
                node.put("lastInform", device.getLastInformTime().toString());
            }
            devices.add(node);
            count++;
        }
        return devices;
    }

    private ObjectNode getDeviceInfo(String deviceId, String paramPath) {
        CpeDevice device = findDevice(deviceId);
        if (device == null) {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("deviceId", device.getDeviceId());
        node.put("serial", device.getSerialNumber());
        node.put("oui", device.getOui());
        node.put("productClass", device.getProductClass());
        node.put("state", device.getState().name());
        node.put("informCount", device.getInformCount());
        if (device.getLastInformTime() != null) {
            node.put("lastInform", device.getLastInformTime().toString());
        }
        if (paramPath != null && !paramPath.isEmpty()) {
            try {
                var paramValue = device.getDataModel().getValue(paramPath);
                node.put("paramPath", paramPath);
                if (paramValue != null) {
                    node.put("paramValue", paramValue.getValue());
                    node.put("paramType", paramValue.getType());
                } else {
                    node.put("paramValue", "");
                }
            } catch (IllegalArgumentException e) {
                node.put("paramPath", paramPath);
                node.put("paramError", e.getMessage());
            }
        }
        return node;
    }

    private CpeDevice findDevice(String deviceId) {
        DeviceManager dm = engine.getDeviceManager();
        if (dm == null) return null;
        return dm.getDevice(deviceId);
    }

    private void triggerRandomInforms(int count) {
        DeviceManager dm = engine.getDeviceManager();
        if (dm == null) return;
        List<CpeDevice> devices = dm.getDevices();
        if (devices.isEmpty()) return;
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < count; i++) {
            CpeDevice device = devices.get(random.nextInt(devices.size()));
            device.triggerInform("2 PERIODIC");
        }
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
