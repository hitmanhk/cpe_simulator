package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.EngineController;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * "Dynamic island" style status capsule: a compact pill showing the engine
 * state; expands on hover to reveal a live summary (devices/online/sessions).
 * Pulses briefly on every state change, and the dot breathes while RUNNING.
 */
public class StatusIsland extends HBox {

    private final Circle dot = new Circle(6);
    private final Label stateLabel = new Label("已停止");
    private final Label summaryLabel = new Label();

    private final Timeline breathe;
    private boolean expanded = false;

    public StatusIsland() {
        getStyleClass().add("island");
        setAlignment(Pos.CENTER);

        stateLabel.getStyleClass().add("island-label");
        summaryLabel.getStyleClass().add("island-summary");
        summaryLabel.setVisible(false);
        summaryLabel.setManaged(false);

        getChildren().addAll(dot, stateLabel, summaryLabel);

        setOnMouseEntered(e -> setExpanded(true));
        setOnMouseExited(e -> setExpanded(false));

        breathe = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(dot.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(900), new KeyValue(dot.opacityProperty(), 0.35)),
                new KeyFrame(Duration.millis(1800), new KeyValue(dot.opacityProperty(), 1.0)));
        breathe.setCycleCount(Timeline.INDEFINITE);

        setState(EngineController.EngineState.STOPPED);
    }

    public void setState(EngineController.EngineState state) {
        switch (state) {
            case STOPPED -> {
                dot.setFill(Color.web("#8b949e"));
                stateLabel.setText("已停止");
                stopBreathing();
            }
            case STARTING -> {
                dot.setFill(Color.web("#f0a13c"));
                stateLabel.setText("启动中…");
                startBreathing();
            }
            case RUNNING -> {
                dot.setFill(Color.web("#2ea043"));
                stateLabel.setText("运行中");
                startBreathing();
            }
            case STOPPING -> {
                dot.setFill(Color.web("#f0a13c"));
                stateLabel.setText("停止中…");
                startBreathing();
            }
            case FAILED -> {
                dot.setFill(Color.web("#f85149"));
                stateLabel.setText("启动失败");
                stopBreathing();
            }
        }
        pulse();
    }

    /** Updates the expanded summary line (called once per second while running). */
    public void setSummary(String text) {
        summaryLabel.setText(text == null ? "" : text);
    }

    private void setExpanded(boolean expand) {
        if (expand == expanded) {
            return;
        }
        expanded = expand;
        if (expand && !summaryLabel.getText().isBlank()) {
            summaryLabel.setManaged(true);
            summaryLabel.setVisible(true);
            FadeTransition fade = new FadeTransition(Duration.millis(180), summaryLabel);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        } else {
            summaryLabel.setVisible(false);
            summaryLabel.setManaged(false);
        }
    }

    /** Brief scale pulse on state transitions — the "island" reacting. */
    private void pulse() {
        ScaleTransition scale = new ScaleTransition(Duration.millis(140), this);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.07);
        scale.setToY(1.07);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    private void startBreathing() {
        breathe.play();
    }

    private void stopBreathing() {
        breathe.stop();
        dot.setOpacity(1.0);
    }
}
