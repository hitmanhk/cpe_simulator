package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import picocli.CommandLine;

import java.nio.file.Path;

public class CpeSimulatorApp {

    public static void main(String[] args) {
        initLogDir();
        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }

    static void initLogDir() {
        try {
            Path jarPath = Path.of(CpeSimulatorApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path jarDir = jarPath.getParent();
            if (jarDir != null) {
                System.setProperty("LOG_DIR", jarDir.toString());
            }
        } catch (Exception e) {
            System.setProperty("LOG_DIR", ".");
        }
    }
}
