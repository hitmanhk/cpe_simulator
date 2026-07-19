package com.cpe.simulator.desktop;

import javafx.application.Application;

/**
 * Non-Application entry point for GUI mode. When running from an unexploded
 * fat JAR on the classpath, JavaFX refuses to launch if the main class
 * extends {@link Application}, so this indirection is required.
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(DesktopApp.class, args);
    }
}
