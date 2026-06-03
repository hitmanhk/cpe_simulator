package com.cpe.simulator.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DaemonHelper {

    public static void daemonize(String pidFile, String logFile, String[] originalArgs) {
        writePidFile(pidFile);

        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse("java"));

        ProcessHandle.current().info().arguments().ifPresent(args -> {
            for (String arg : args) {
                command.add(arg);
            }
        });

        if (command.size() <= 1) {
            command.clear();
            command.add("java");
            command.add("--enable-preview");
            command.add("-jar");
            command.add(getJarPath());
            for (String arg : originalArgs) {
                if (!"--daemon".equals(arg)) {
                    command.add(arg);
                }
            }
            command.add("--foreground");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();

        if (logFile != null && !logFile.isEmpty()) {
            try {
                Path logPath = Path.of(logFile);
                Files.createDirectories(logPath.getParent());
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
                pb.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            } catch (IOException e) {
                System.err.println("Warning: Could not set up log file: " + e.getMessage());
            }
        }

        try {
            Process process = pb.start();
            System.out.println("Daemon started with PID: " + process.pid());
            System.out.println("PID file: " + pidFile);
            if (logFile != null) {
                System.out.println("Log file: " + logFile);
            }
        } catch (IOException e) {
            System.err.println("Failed to start daemon: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void writePidFile(String pidFile) {
        try {
            long pid = ProcessHandle.current().pid();
            Files.writeString(Path.of(pidFile), String.valueOf(pid));
        } catch (IOException e) {
            System.err.println("Warning: Could not write PID file: " + e.getMessage());
        }
    }

    public static void deletePidFile(String pidFile) {
        try {
            Files.deleteIfExists(Path.of(pidFile));
        } catch (IOException e) {
            System.err.println("Warning: Could not delete PID file: " + e.getMessage());
        }
    }

    public static long readPid(String pidFile) {
        try {
            String content = Files.readString(Path.of(pidFile)).trim();
            return Long.parseLong(content);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String getJarPath() {
        try {
            return Path.of(DaemonHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception e) {
            return "cpe-simulator.jar";
        }
    }
}
