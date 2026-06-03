package com.cpe.simulator.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

@Command(name = "version", description = "Print version information")
public class VersionCommand implements Runnable, IVersionProvider {

    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final String BUILD_TIME = "2026-06-03";

    @Override
    public void run() {
        System.out.println("CPE Simulator v" + VERSION);
        System.out.println("Build time: " + BUILD_TIME);
        System.out.println("JDK: " + System.getProperty("java.version"));
    }

    @Override
    public String[] getVersion() {
        return new String[]{
            "CPE Simulator v" + VERSION,
            "Build time: " + BUILD_TIME,
            "JDK: " + System.getProperty("java.version")
        };
    }
}
