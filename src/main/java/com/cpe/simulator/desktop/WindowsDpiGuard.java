package com.cpe.simulator.desktop;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Works around a Windows multi-monitor mixed-DPI defect in JavaFX:
 * the native (glass) layer caches the stage minimum size in physical pixels
 * computed with the output scale at the time it was set. When the window is
 * dragged to a monitor with a different scale (e.g. 4K 150% -&gt; 2K 100%),
 * the stale physical minimum keeps clamping the window (can only enlarge,
 * never shrink) and the render surface is sized smaller than the window,
 * leaving blank bands on the right and bottom edges.
 * <p>
 * The guard watches the screen/output-scale under the window; on change it
 * re-applies the minimum size (forcing the native cache to be recomputed
 * with the new scale) and nudges the window size by one pixel to force the
 * native window rectangle and the render surface back into sync.
 */
public final class WindowsDpiGuard {

    private final Stage stage;
    private final PauseTransition settle = new PauseTransition(Duration.millis(60));
    private Screen lastScreen;
    private double lastScaleX = -1;
    private boolean adjusting;

    public WindowsDpiGuard(Stage stage) {
        this.stage = stage;
        settle.setOnFinished(e -> applyFix());
        ChangeListener<Number> geometryListener = (obs, oldValue, newValue) -> onGeometryChanged();
        stage.xProperty().addListener(geometryListener);
        stage.yProperty().addListener(geometryListener);
    }

    private void onGeometryChanged() {
        if (adjusting) {
            return;
        }
        Screen screen = currentScreen();
        if (screen == null) {
            return;
        }
        double scaleX = screen.getOutputScaleX();
        boolean changed = lastScreen != null
                && (!screen.equals(lastScreen) || scaleX != lastScaleX);
        lastScreen = screen;
        lastScaleX = scaleX;
        if (changed) {
            // 等待 glass 的 WM_DPICHANGED 处理完成后再修正，避免互相覆盖
            settle.playFromStart();
        }
    }

    private void applyFix() {
        if (adjusting) {
            return;
        }
        adjusting = true;
        try {
            double minW = stage.getMinWidth();
            double minH = stage.getMinHeight();
            // 清零再恢复：强制原生层按新缩放重新计算最小尺寸的物理像素，
            // 解除"只能拉大不能拉小"的陈旧钳制
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setMinWidth(minW);
            stage.setMinHeight(minH);
            // 尺寸抖动 1px：强制原生窗口矩形与渲染表面重新同步，消除右/下空白
            double w = stage.getWidth();
            double h = stage.getHeight();
            stage.setWidth(w + 1);
            stage.setHeight(h + 1);
            stage.setWidth(w);
            stage.setHeight(h);
        } finally {
            adjusting = false;
        }
    }

    private Screen currentScreen() {
        ObservableList<Screen> screens = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()));
        return screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
    }
}
