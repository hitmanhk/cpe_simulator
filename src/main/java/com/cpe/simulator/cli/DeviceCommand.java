package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "device",
    description = "Device management commands",
    subcommands = {
        DeviceCommand.ListDevices.class,
        DeviceCommand.GetDevice.class,
        DeviceCommand.AddDevice.class,
        DeviceCommand.RemoveDevice.class,
        DeviceCommand.InformDevice.class
    }
)
public class DeviceCommand implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void run() {
        System.out.println("Usage: cpe-sim device <list|get|add|remove|inform>");
    }

    @Command(name = "list", description = "List devices")
    static class ListDevices implements Runnable {

        @Option(names = {"--online"}, description = "Show only online devices")
        boolean online;

        @Option(names = {"--limit"}, description = "Maximum number of devices to show", defaultValue = "100")
        int limit;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("online", online);
            params.put("limit", limit);

            JsonNode response = UdsClient.send("device-list", params, socketPath);
            if (response == null) return;

            JsonNode devices = response.get("devices");
            if (devices == null || !devices.isArray() || devices.isEmpty()) {
                System.out.println("No devices found");
                return;
            }

            System.out.printf("%-35s %-12s %-10s %-8s %-8s%n", "Device ID", "Serial", "State", "Informs", "Last Inform");
            System.out.println("-".repeat(85));

            for (JsonNode device : devices) {
                String deviceId = device.has("deviceId") ? device.get("deviceId").asText() : "";
                String serial = device.has("serial") ? device.get("serial").asText() : "";
                String state = device.has("state") ? device.get("state").asText() : "";
                long informs = device.has("informCount") ? device.get("informCount").asLong() : 0;
                String lastInform = device.has("lastInform") ? device.get("lastInform").asText() : "N/A";
                if (lastInform.length() > 19) {
                    lastInform = lastInform.substring(0, 19);
                }
                System.out.printf("%-35s %-12s %-10s %-8d %-8s%n", deviceId, serial, state, informs, lastInform);
            }

            int count = response.has("count") ? response.get("count").asInt() : 0;
            System.out.println("\nShowing " + count + " device(s)");
        }
    }

    @Command(name = "get", description = "Get device parameters")
    static class GetDevice implements Runnable {

        @Parameters(index = "0", description = "Device ID")
        String deviceId;

        @Option(names = {"--path", "-p"}, description = "Parameter path to query")
        String path;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("deviceId", deviceId);
            if (path != null) {
                params.put("path", path);
            }

            JsonNode response = UdsClient.send("device-get", params, socketPath);
            if (response == null) return;

            JsonNode device = response.get("device");
            if (device == null) {
                System.out.println("Device not found");
                return;
            }

            System.out.println("=== Device Information ===");
            System.out.printf("  Device ID:     %s%n", device.has("deviceId") ? device.get("deviceId").asText() : "N/A");
            System.out.printf("  Serial:        %s%n", device.has("serial") ? device.get("serial").asText() : "N/A");
            System.out.printf("  OUI:           %s%n", device.has("oui") ? device.get("oui").asText() : "N/A");
            System.out.printf("  Product Class: %s%n", device.has("productClass") ? device.get("productClass").asText() : "N/A");
            System.out.printf("  State:         %s%n", device.has("state") ? device.get("state").asText() : "N/A");
            System.out.printf("  Inform Count:  %d%n", device.has("informCount") ? device.get("informCount").asLong() : 0);
            System.out.printf("  Last Inform:   %s%n", device.has("lastInform") ? device.get("lastInform").asText() : "N/A");

            if (device.has("paramPath")) {
                System.out.println("\n=== Parameter ===");
                System.out.printf("  Path:  %s%n", device.get("paramPath").asText());
                if (device.has("paramValue")) {
                    System.out.printf("  Value: %s%n", device.get("paramValue").asText());
                } else if (device.has("paramError")) {
                    System.out.printf("  Error: %s%n", device.get("paramError").asText());
                }
            }
        }
    }

    @Command(name = "add", description = "Add devices dynamically")
    static class AddDevice implements Runnable {

        @Option(names = {"--count", "-c"}, description = "Number of devices to add", defaultValue = "1")
        int count;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("count", count);

            JsonNode response = UdsClient.send("device-add", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }

    @Command(name = "remove", description = "Remove a device")
    static class RemoveDevice implements Runnable {

        @Parameters(index = "0", description = "Device ID to remove")
        String deviceId;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("deviceId", deviceId);

            JsonNode response = UdsClient.send("device-remove", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }

    @Command(name = "inform", description = "Trigger device Inform")
    static class InformDevice implements Runnable {

        @Parameters(index = "0", description = "Device ID", arity = "0..1")
        String deviceId;

        @Option(names = {"--random", "-r"}, description = "Trigger random informs on N devices")
        int random;

        @Option(names = {"--socket-path"}, description = "UDS socket path")
        String socketPath;

        @Override
        public void run() {
            ObjectNode params = MAPPER.createObjectNode();
            if (random > 0) {
                params.put("random", random);
            } else if (deviceId != null) {
                params.put("deviceId", deviceId);
            } else {
                System.err.println("Error: Either provide a device ID or use --random");
                return;
            }

            JsonNode response = UdsClient.send("device-inform", params, socketPath);
            if (response != null && response.has("message")) {
                System.out.println(response.get("message").asText());
            }
        }
    }
}
