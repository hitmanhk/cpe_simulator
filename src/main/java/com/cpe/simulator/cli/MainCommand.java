package com.cpe.simulator.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "cpe-sim",
    description = "High-performance TR-069 CWMP CPE Simulator",
    mixinStandardHelpOptions = true,
    versionProvider = VersionCommand.class,
    subcommands = {
        StartCommand.class,
        StopCommand.class,
        StatusCommand.class,
        DeviceCommand.class,
        EventCommand.class,
        VersionCommand.class
    }
)
public class MainCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
