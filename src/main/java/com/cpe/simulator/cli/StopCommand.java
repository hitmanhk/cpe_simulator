package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "stop", description = "Stop the running simulator")
public class StopCommand implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Option(names = {"--force", "-f"}, description = "Force stop")
    boolean force;

    @Option(names = {"--timeout", "-t"}, description = "Timeout in seconds", defaultValue = "30")
    int timeout;

    @Option(names = {"--socket-path"}, description = "UDS socket path")
    String socketPath;

    @Override
    public void run() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("force", force);
        params.put("timeout", timeout);

        JsonNode response = UdsClient.send("stop", params, socketPath);
        if (response != null) {
            System.out.println(response.has("message") ? response.get("message").asText() : "Stop command sent");
        }
    }
}
