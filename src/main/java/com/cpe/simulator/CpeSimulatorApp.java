package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import picocli.CommandLine;

public class CpeSimulatorApp {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }
}
