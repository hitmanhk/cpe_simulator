package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "event",
    description = "Trigger device events",
    subcommands = {
        EventCommand.RebootEvent.class,
        EventCommand.FactoryResetEvent.class,
        EventCommand.ValueChangeEvent.class
    }
)
public class EventCommand implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void run() {
        System.out.println("Usage: cpe-sim event <reboot|factory-reset|value-change>");
    }

    @Command(name = "reboot", description = "Trigger device reboot")
    static class RebootEvent implements Runnable {

        @Parameters(index = "0", description = "Device ID")
        String deviceId;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("deviceId", deviceId);

            JsonNode response = UdsClient.send("event-reboot", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }

    @Command(name = "factory-reset", description = "Trigger factory reset")
    static class FactoryResetEvent implements Runnable {

        @Parameters(index = "0", description = "Device ID")
        String deviceId;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("deviceId", deviceId);

            JsonNode response = UdsClient.send("event-factory-reset", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }

    @Command(name = "value-change", description = "Trigger value change event")
    static class ValueChangeEvent implements Runnable {

        @Parameters(index = "0", description = "Device ID")
        String deviceId;

        @Option(names = {"--param", "-p"}, description = "Parameter path", required = true)
        String param;

        @Option(names = {"--value", "-v"}, description = "New value", required = true)
        String value;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("deviceId", deviceId);
            params.put("param", param);
            params.put("value", value);

            JsonNode response = UdsClient.send("event-value-change", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }
}
