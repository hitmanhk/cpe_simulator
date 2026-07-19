package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import com.cpe.simulator.desktop.DesktopLauncher;
import picocli.CommandLine;

import java.nio.file.Path;

public class CpeSimulatorApp {

    public static void main(String[] args) {
        initLogDir();
        if (isGuiMode(args)) {
            DesktopLauncher.main(args);
            return;
        }
        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }

    /**
     * GUI mode is the default when launching without arguments (e.g. double
     * clicking the app); any CLI subcommand keeps the original behavior.
     */
    static boolean isGuiMode(String[] args) {
        return args.length == 0 || (args.length == 1 && "--gui".equals(args[0]));
    }

    static void initLogDir() {
        try {
            Path jarPath = Path.of(CpeSimulatorApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path jarDir = jarPath.getParent();
            if (jarDir != null) {
                if (java.nio.file.Files.isWritable(jarDir)) {
                    System.setProperty("LOG_DIR", jarDir.toString());
                } else {
                    // 安装到 Program Files 等只读目录时（jpackage 安装包），
                    // 日志回退到用户目录，避免 logback 初始化失败
                    System.setProperty("LOG_DIR", Path.of(System.getProperty("user.home"),
                            ".cpe-simulator").toString());
                }
            }
        } catch (Exception e) {
            System.setProperty("LOG_DIR", ".");
        }
    }
}
