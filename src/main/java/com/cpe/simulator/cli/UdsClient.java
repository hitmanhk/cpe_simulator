package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UdsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER_SIZE = 1024 * 1024;

    public static String getDefaultSocketPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String temp = System.getenv("TEMP");
            if (temp == null || temp.isEmpty()) {
                temp = System.getProperty("java.io.tmpdir");
            }
            return temp + "\\cpe-sim.sock";
        }
        return "/tmp/cpe-sim.sock";
    }

    public static JsonNode send(String action, JsonNode params, String socketPathStr) {
        if (socketPathStr == null || socketPathStr.isEmpty()) {
            socketPathStr = getDefaultSocketPath();
        }
        Path socketPath = Path.of(socketPathStr);
        if (!Files.exists(socketPath)) {
            System.err.println("Error: Simulator is not running (socket not found at " + socketPathStr + ")");
            return null;
        }

        try {
            SocketAddress address = UnixDomainSocketAddress.of(socketPath);
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(address);

                ObjectNode request = MAPPER.createObjectNode();
                request.put("action", action);
                if (params != null) {
                    request.set("params", params);
                }

                byte[] requestBytes = MAPPER.writeValueAsBytes(request);
                channel.write(ByteBuffer.wrap(requestBytes));
                channel.shutdownOutput();

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                StringBuilder responseBuilder = new StringBuilder();
                int bytesRead;
                while ((bytesRead = channel.read(buffer)) > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[bytesRead];
                    buffer.get(bytes);
                    responseBuilder.append(new String(bytes, StandardCharsets.UTF_8));
                    buffer.clear();
                }

                String responseStr = responseBuilder.toString();
                if (responseStr.isEmpty()) {
                    System.err.println("Error: Empty response from simulator");
                    return null;
                }

                JsonNode response = MAPPER.readTree(responseStr);
                if (response.has("error")) {
                    System.err.println("Error: " + response.get("error").asText());
                    return null;
                }
                return response;
            }
        } catch (java.net.ConnectException e) {
            System.err.println("Error: Simulator is not running (connection refused)");
            return null;
        } catch (Exception e) {
            System.err.println("Error communicating with simulator: " + e.getMessage());
            return null;
        }
    }

    public static JsonNode send(String action, JsonNode params) {
        return send(action, params, null);
    }

    public static JsonNode send(String action) {
        return send(action, null, null);
    }
}
