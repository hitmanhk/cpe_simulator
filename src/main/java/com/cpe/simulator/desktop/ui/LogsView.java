package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import com.cpe.simulator.desktop.bridge.UiLogSink;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Live log view fed by the UiLogAppender logback bridge. Subscribes at
 * construction time so startup failures are visible too — not just logs
 * emitted while the engine runs. Rendering is throttled to 4 batches/second
 * and capped at {@value #BUFFER_LIMIT} rows.
 */
public class LogsView extends AbstractView implements ViewSearcher {

    private static final int BUFFER_LIMIT = 5000;
    private static final int DRAIN_MILLIS = 250;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final ConcurrentLinkedQueue<UiLogSink.LogEntry> queue = new ConcurrentLinkedQueue<>();
    private final ObservableList<UiLogSink.LogEntry> visible = FXCollections.observableArrayList();
    private final List<UiLogSink.LogEntry> buffer = new ArrayList<>(BUFFER_LIMIT + 16);

    private final ListView<UiLogSink.LogEntry> listView = new ListView<>(visible);
    private final ComboBox<String> levelFilter = new ComboBox<>(FXCollections.observableArrayList(
            "全部", "DEBUG", "INFO", "WARN", "ERROR"));
    private final TextField searchField = new TextField();
    private final CheckBox followCheck = new CheckBox("自动滚动");

    /** 全局搜索条（Ctrl+F）状态，与本地搜索框过滤叠加。 */
    private String globalQuery = "";
    private SearchOptions globalOptions = SearchOptions.DEFAULT;

    private final Consumer<UiLogSink.LogEntry> sinkListener = queue::offer;
    private final Timeline drainTimeline;

    public LogsView(EngineController engineController, UiEventBridge eventBridge) {
        super(engineController, eventBridge);

        Label title = new Label("日志");
        title.getStyleClass().add("view-title");

        levelFilter.setValue("全部");
        searchField.setPromptText("搜索（设备 ID / 文本）…");
        searchField.setPrefWidth(220);
        followCheck.setSelected(true);
        levelFilter.valueProperty().addListener((obs, o, n) -> refilter());
        searchField.textProperty().addListener((obs, o, n) -> refilter());

        javafx.scene.control.Button clearButton = new javafx.scene.control.Button("清空");
        clearButton.setOnAction(e -> {
            buffer.clear();
            visible.clear();
            queue.clear();
        });

        HBox toolbar = new HBox(8, new Label("级别:"), levelFilter, searchField,
                followCheck, clearButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        listView.setCellFactory(lv -> new LogCell());
        listView.setPlaceholder(new Label("暂无日志"));
        javafx.scene.layout.VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().addAll(title, toolbar, listView);

        UiLogSink.addListener(sinkListener);
        drainTimeline = new Timeline(new KeyFrame(Duration.millis(DRAIN_MILLIS), e -> drain()));
        drainTimeline.setCycleCount(Animation.INDEFINITE);
        drainTimeline.play();
    }

    private void drain() {
        UiLogSink.LogEntry entry;
        boolean added = false;
        while ((entry = queue.poll()) != null) {
            buffer.add(entry);
            if (buffer.size() > BUFFER_LIMIT) {
                int overflow = buffer.size() - BUFFER_LIMIT;
                buffer.subList(0, overflow).clear();
                // 同步修剪可见列表中被淘汰的行
                int removeCount = 0;
                for (int i = 0; i < overflow && i < visible.size(); i++) {
                    removeCount++;
                }
                if (removeCount > 0) {
                    visible.remove(0, removeCount);
                }
            }
            if (passesFilter(entry)) {
                visible.add(entry);
                added = true;
            }
        }
        if (added && followCheck.isSelected() && !visible.isEmpty()) {
            listView.scrollTo(visible.size() - 1);
        }
    }

    private boolean passesFilter(UiLogSink.LogEntry entry) {
        String level = levelFilter.getValue();
        if (!"全部".equals(level) && !level.equals(entry.level())) {
            return false;
        }
        String query = searchField.getText();
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            if (!entry.message().toLowerCase(Locale.ROOT).contains(q)
                    && !entry.loggerName().toLowerCase(Locale.ROOT).contains(q)) {
                return false;
            }
        }
        if (!globalQuery.isEmpty()) {
            return globalOptions.matches(globalQuery, entry.message())
                    || globalOptions.matches(globalQuery, entry.loggerName());
        }
        return true;
    }

    // ---------------- 全局搜索（ViewSearcher） ----------------

    @Override
    public int applySearch(String query, SearchOptions options) {
        globalQuery = query == null ? "" : query.trim();
        globalOptions = options != null ? options : SearchOptions.DEFAULT;
        refilter();
        return visible.size();
    }

    @Override
    public void clearSearch() {
        globalQuery = "";
        refilter();
    }

    /** Re-applies the current filter over the whole buffer. */
    private void refilter() {
        List<UiLogSink.LogEntry> kept = new ArrayList<>(buffer.size());
        for (UiLogSink.LogEntry entry : buffer) {
            if (passesFilter(entry)) {
                kept.add(entry);
            }
        }
        visible.setAll(kept);
        if (followCheck.isSelected() && !visible.isEmpty()) {
            listView.scrollTo(visible.size() - 1);
        }
    }

    @Override
    public void dispose() {
        UiLogSink.removeListener(sinkListener);
        if (drainTimeline != null) {
            drainTimeline.stop();
        }
    }

    private static class LogCell extends ListCell<UiLogSink.LogEntry> {
        @Override
        protected void updateItem(UiLogSink.LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(TIME_FORMAT.format(entry.timestamp()) + " [" + entry.level() + "] " + entry.message());
            setStyle(switch (entry.level()) {
                case "ERROR" -> "-fx-text-fill: #cf222e;";
                case "WARN" -> "-fx-text-fill: #bf8700;";
                case "DEBUG" -> "-fx-text-fill: -color-fg-muted;";
                default -> "";
            });
            setFont(javafx.scene.text.Font.font("Monospaced", 12));
        }
    }
}
