package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import com.cpe.simulator.engine.SimulationEngine;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Dashboard: status cards polled once per second from the engine, plus two
 * live charts (inform rate and online device count) with a 5-minute sliding
 * window sampled at 1 Hz.
 */
public class DashboardView extends AbstractView {

    private static final int POLL_MILLIS = 1000;
    private static final int WINDOW_SECONDS = 300;

    private final Label modeValue = new Label("-");
    private final Label totalDevicesValue = new Label("-");
    private final Label onlineDevicesValue = new Label("-");
    private final Label activeSessionsValue = new Label("-");
    private final Label totalInformsValue = new Label("-");
    private final Label uptimeValue = new Label("-");
    private final Label memoryValue = new Label("-");
    private final Label threadsValue = new Label("-");

    private final XYChart.Series<Number, Number> informRateSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> onlineSeries = new XYChart.Series<>();

    private Timeline pollTimeline;
    private long lastTotalInforms = -1;
    private long lastPollNanos;
    private int sampleIndex = 0;
    private Consumer<String> summaryListener;

    public DashboardView(EngineController engineController, UiEventBridge eventBridge) {
        super(engineController, eventBridge);

        Label title = new Label("仪表盘");
        title.getStyleClass().add("view-title");

        GridPane cards = new GridPane();
        cards.setHgap(12);
        cards.setVgap(12);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cards.getColumnConstraints().add(cc);
        }
        cards.add(card("运行模式", modeValue), 0, 0);
        cards.add(card("设备总数", totalDevicesValue), 1, 0);
        cards.add(card("在线设备", onlineDevicesValue), 2, 0);
        cards.add(card("活跃会话", activeSessionsValue), 3, 0);
        cards.add(card("累计 Inform", totalInformsValue), 0, 1);
        cards.add(card("运行时长", uptimeValue), 1, 1);
        cards.add(card("JVM 内存", memoryValue), 2, 1);
        cards.add(card("线程数", threadsValue), 3, 1);

        HBox charts = new HBox(12, buildChart("Inform 速率（条/秒）", informRateSeries),
                buildChart("在线设备数", onlineSeries));
        HBox.setHgrow(charts.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(charts.getChildren().get(1), Priority.ALWAYS);
        charts.getChildren().forEach(c -> ((LineChart) c).setPrefHeight(280));

        VBox.setVgrow(charts, Priority.ALWAYS);
        getChildren().addAll(title, cards, charts);
    }

    /** Injected by MainWindow so the bottom status bar mirrors the summary. */
    public void setSummaryListener(Consumer<String> listener) {
        this.summaryListener = listener;
    }

    private VBox card(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        valueLabel.getStyleClass().add("card-value");
        VBox card = new VBox(4, titleLabel, valueLabel);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("card");
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private LineChart buildChart(String title, XYChart.Series<Number, Number> series) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("时间（秒）");
        xAxis.setAutoRanging(true);
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.getData().add(series);
        chart.setMaxWidth(Double.MAX_VALUE);
        return chart;
    }

    @Override
    public void onEngineStarted() {
        resetSeries();
        if (pollTimeline == null) {
            pollTimeline = new Timeline(new KeyFrame(Duration.millis(POLL_MILLIS), e -> poll()));
            pollTimeline.setCycleCount(Animation.INDEFINITE);
        }
        pollTimeline.play();
        poll(); // 立即刷新一次
    }

    @Override
    public void onEngineStopped() {
        if (pollTimeline != null) {
            pollTimeline.stop();
        }
        resetSeries();
        modeValue.setText("-");
        totalDevicesValue.setText("-");
        onlineDevicesValue.setText("-");
        activeSessionsValue.setText("-");
        totalInformsValue.setText("-");
        uptimeValue.setText("-");
        memoryValue.setText("-");
        threadsValue.setText("-");
        if (summaryListener != null) {
            summaryListener.accept("");
        }
    }

    @Override
    public void dispose() {
        if (pollTimeline != null) {
            pollTimeline.stop();
            pollTimeline = null;
        }
    }

    private void resetSeries() {
        informRateSeries.getData().clear();
        onlineSeries.getData().clear();
        lastTotalInforms = -1;
        sampleIndex = 0;
    }

    private void poll() {
        SimulationEngine.SimulationStatus status = engineController.getStatus();
        if (status == null) {
            return;
        }
        modeValue.setText(status.mode());
        totalDevicesValue.setText(String.valueOf(status.totalDevices()));
        onlineDevicesValue.setText(String.valueOf(status.onlineDevices()));
        activeSessionsValue.setText(String.valueOf(status.activeSessions()));
        totalInformsValue.setText(String.valueOf(status.totalInforms()));
        uptimeValue.setText(formatDuration(status.uptime()));
        memoryValue.setText(formatMb(status.jvmMemoryUsed()) + " / " + formatMb(status.jvmMemoryMax()));
        threadsValue.setText(String.valueOf(status.threadCount()));

        long now = System.nanoTime();
        if (lastTotalInforms >= 0 && lastPollNanos > 0) {
            double elapsedSeconds = (now - lastPollNanos) / 1_000_000_000.0;
            double rate = elapsedSeconds > 0 ? (status.totalInforms() - lastTotalInforms) / elapsedSeconds : 0;
            appendPoint(informRateSeries, sampleIndex, Math.max(0, rate));
            appendPoint(onlineSeries, sampleIndex, status.onlineDevices());
            sampleIndex++;
        }
        lastTotalInforms = status.totalInforms();
        lastPollNanos = now;

        if (summaryListener != null) {
            summaryListener.accept(String.format("模式 %s ｜ 设备 %d ｜ 在线 %d ｜ 会话 %d ｜ 累计 Inform %d",
                    status.mode(), status.totalDevices(), status.onlineDevices(),
                    status.activeSessions(), status.totalInforms()));
        }
    }

    private void appendPoint(XYChart.Series<Number, Number> series, int x, double y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > WINDOW_SECONDS) {
            series.getData().remove(0);
        }
    }

    private static String formatMb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    private static String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%dh %02dm %02ds", h, m, s) : String.format("%dm %02ds", m, s);
    }
}
