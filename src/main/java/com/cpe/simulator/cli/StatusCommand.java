package com.cpe.simulator.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "status", description = "Query simulator status")
public class StatusCommand implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Option(names = {"--json"}, description = "JSON output")
    boolean json;

    @Override
    public void run() {
        JsonNode response = UdsClient.send("status");
        if (response == null) {
            return;
        }

        if (json) {
            try {
                ObjectMapper prettyMapper = new ObjectMapper();
                prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
                System.out.println(prettyMapper.writeValueAsString(response));
            } catch (Exception e) {
                System.out.println(response.toString());
            }
            return;
        }

        System.out.println("=== CPE Simulator Status ===");
        System.out.printf("  Running:          %s%n", response.has("running") && response.get("running").asBoolean() ? "Yes" : "No");
        System.out.printf("  Mode:             %s%n", response.has("mode") ? response.get("mode").asText() : "N/A");
        System.out.printf("  Total Devices:    %d%n", response.has("totalDevices") ? response.get("totalDevices").asInt() : 0);
        System.out.printf("  Online Devices:   %d%n", response.has("onlineDevices") ? response.get("onlineDevices").asInt() : 0);
        System.out.printf("  Active Sessions:  %d%n", response.has("activeSessions") ? response.get("activeSessions").asInt() : 0);
        System.out.printf("  Total Informs:    %d%n", response.has("totalInforms") ? response.get("totalInforms").asLong() : 0);
        System.out.printf("  Uptime:           %s%n", response.has("uptimeFormatted") ? response.get("uptimeFormatted").asText() : "N/A");

        long memUsed = response.has("jvmMemoryUsed") ? response.get("jvmMemoryUsed").asLong() : 0;
        long memMax = response.has("jvmMemoryMax") ? response.get("jvmMemoryMax").asLong() : 0;
        System.out.printf("  JVM Memory:       %d MB / %d MB%n", memUsed / (1024 * 1024), memMax / (1024 * 1024));
        System.out.printf("  Threads:          %d%n", response.has("threadCount") ? response.get("threadCount").asInt() : 0);
    }
}
