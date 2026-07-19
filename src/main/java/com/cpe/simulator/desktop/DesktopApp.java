package com.cpe.simulator.desktop;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import com.cpe.simulator.desktop.bridge.UiLogAppender;
import com.cpe.simulator.desktop.ui.AppIcons;
import com.cpe.simulator.desktop.ui.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application for the CPE Simulator desktop mode ("ACS 设备模拟器").
 * Uses the Cupertino (macOS-style) AtlantaFX theme with a light/dark toggle.
 */
public class DesktopApp extends Application implements MainWindow.ThemeController {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private MainWindow mainWindow;
    private Scene scene;
    private boolean dark;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        UiLogAppender.install();

        mainWindow = new MainWindow(this);
        scene = new Scene(mainWindow.getRoot(), 1360, 840);
        scene.getStylesheets().add(getClass().getResource("/desktop/app.css").toExternalForm());
        mainWindow.installShortcuts(scene);
        stage.setScene(scene);
        stage.setTitle("ACS 设备模拟器");
        stage.getIcons().addAll(AppIcons.all());
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(event -> {
            event.consume();
            shutdown();
        });
        // 多显示器混合 DPI 拖动防护：重算最小尺寸缓存 + 同步渲染表面（见类注释）
        new WindowsDpiGuard(stage);
        stage.show();
    }

    @Override
    public void toggle() {
        dark = !dark;
        Application.setUserAgentStylesheet(dark
                ? new CupertinoDark().getUserAgentStylesheet()
                : new CupertinoLight().getUserAgentStylesheet());
        scene.getRoot().pseudoClassStateChanged(DARK, dark);
    }

    @Override
    public boolean isDark() {
        return dark;
    }

    @Override
    public void stop() {
        if (mainWindow != null) {
            mainWindow.dispose();
        }
        UiLogAppender.uninstall();
    }

    private void shutdown() {
        mainWindow.dispose();
        UiLogAppender.uninstall();
        if (!mainWindow.requestEngineStopForExit()) {
            Platform.exit();
        }
    }
}
