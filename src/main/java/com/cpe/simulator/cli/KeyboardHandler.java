package com.cpe.simulator.cli;

import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.DeviceManager;
import com.cpe.simulator.engine.SimulationEngine;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyboardHandler {

    private final SimulationEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public KeyboardHandler(SimulationEngine engine) {
        this.engine = engine;
    }

    public void start() {
        running.set(true);
        Thread.ofVirtual().name("keyboard-handler").start(this::run);
    }

    public void stop() {
        running.set(false);
    }

    private void run() {
        printHelp();
        if (System.console() == null && !System.in.markSupported()) {
            try {
                while (running.get()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        try (Scanner scanner = new Scanner(System.in)) {
            while (running.get()) {
                System.out.print("> ");
                System.out.flush();
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim().toLowerCase();
                if (line.isEmpty()) continue;
                processKey(line);
            }
        } catch (Exception e) {
            if (running.get()) {
                System.err.println("Keyboard handler error: " + e.getMessage());
            }
        }
    }

    private void processKey(String key) {
        switch (key) {
            case "s" -> printStatus();
            case "i" -> triggerAllInforms();
            case "r" -> triggerRandomReboot();
            case "q" -> {
                System.out.println("Shutting down...");
                running.set(false);
                engine.stop();
                System.exit(0);
            }
            case "h" -> printHelp();
            default -> System.out.println("Unknown command: " + key + " (press 'h' for help)");
        }
    }

    private void printStatus() {
        var status = engine.getStatus();
        System.out.println("=== Simulator Status ===");
        System.out.printf("  Running:        %s%n", status.running() ? "Yes" : "No");
        System.out.printf("  Mode:           %s%n", status.mode());
        System.out.printf("  Total Devices:  %d%n", status.totalDevices());
        System.out.printf("  Online Devices: %d%n", status.onlineDevices());
        System.out.printf("  Active Sessions:%d%n", status.activeSessions());
        System.out.printf("  Total Informs:  %d%n", status.totalInforms());
        System.out.printf("  Uptime:         %s%n", formatDuration(status.uptime()));
        System.out.printf("  JVM Memory:     %d MB / %d MB%n",
            status.jvmMemoryUsed() / (1024 * 1024),
            status.jvmMemoryMax() / (1024 * 1024));
        System.out.printf("  Threads:        %d%n", status.threadCount());
    }

    private void triggerAllInforms() {
        DeviceManager dm = engine.getDeviceManager();
        if (dm == null) {
            System.out.println("Device manager not available");
            return;
        }
        List<CpeDevice> devices = dm.getDevices();
        int count = 0;
        for (CpeDevice device : devices) {
            if (device.getState() == CpeDevice.DeviceState.ONLINE) {
                device.triggerInform("2 PERIODIC");
                count++;
            }
        }
        System.out.println("Triggered inform for " + count + " online device(s)");
    }

    private void triggerRandomReboot() {
        DeviceManager dm = engine.getDeviceManager();
        if (dm == null) {
            System.out.println("Device manager not available");
            return;
        }
        List<CpeDevice> devices = dm.getDevices();
        if (devices.isEmpty()) {
            System.out.println("No devices available");
            return;
        }
        java.util.Random random = new java.util.Random();
        CpeDevice device = devices.get(random.nextInt(devices.size()));
        System.out.println("Scheduling reboot for: " + device.getDeviceId());
        device.scheduleReboot(0);
    }

    private void printHelp() {
        System.out.println("Keyboard shortcuts:");
        System.out.println("  s - Print status");
        System.out.println("  i - Trigger all informs");
        System.out.println("  r - Trigger random reboot");
        System.out.println("  q - Quit (graceful shutdown)");
        System.out.println("  h - Print this help");
    }

    private String formatDuration(java.time.Duration duration) {
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
