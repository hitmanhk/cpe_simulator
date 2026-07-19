package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.common.event.SimEvent;
import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Message monitor: live CWMP RPC stream and XMPP event stream fed by the
 * UiEventBridge batch pipeline. Each tab keeps a ring buffer capped at
 * {@value #BUFFER_LIMIT} rows so long-running sessions can't exhaust memory.
 */
public class MonitorView extends AbstractView implements ViewSearcher {

    private static final int BUFFER_LIMIT = 5000;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public record RpcRow(String time, String deviceId, String session, String direction,
                         String method, String status, String details) {
    }

    public record XmppRow(String time, String deviceId, String event, String details) {
    }

    private final ObservableList<RpcRow> rpcRows = FXCollections.observableArrayList();
    private final ObservableList<XmppRow> xmppRows = FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<RpcRow> filteredRpc =
            new javafx.collections.transformation.FilteredList<>(rpcRows, r -> true);
    private final javafx.collections.transformation.FilteredList<XmppRow> filteredXmpp =
            new javafx.collections.transformation.FilteredList<>(xmppRows, r -> true);

    private final TextField rpcDeviceFilter = new TextField();
    private final TextField rpcMethodFilter = new TextField();
    private final TextField xmppDeviceFilter = new TextField();
    private final CheckBox pauseCheck = new CheckBox("暂停");

    /** 全局搜索条（Ctrl+F）状态（显示侧过滤，叠加在采集侧过滤之上）。 */
    private String globalQuery = "";
    private SearchOptions globalOptions = SearchOptions.DEFAULT;

    private final Consumer<List<SimEvent>> batchHandler = this::onBatch;

    public MonitorView(EngineController engineController, UiEventBridge eventBridge) {
        super(engineController, eventBridge);

        Label title = new Label("消息监视");
        title.getStyleClass().add("view-title");

        TabPane tabs = new TabPane(buildRpcTab(), buildXmppTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().addAll(title, tabs);

        eventBridge.addHandler(batchHandler);
    }

    private Tab buildRpcTab() {
        TableView<RpcRow> table = new TableView<>(filteredRpc);
        table.getColumns().setAll(
                col("时间", 110, RpcRow::time),
                col("设备 ID", 220, RpcRow::deviceId),
                col("方向", 110, RpcRow::direction),
                col("方法", 180, RpcRow::method),
                statusCol(),
                col("详情", 300, RpcRow::details),
                col("会话", 120, r -> abbreviate(r.session())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("暂无 CWMP RPC 消息"));

        rpcDeviceFilter.setPromptText("设备过滤…");
        rpcDeviceFilter.setPrefWidth(160);
        rpcMethodFilter.setPromptText("方法过滤…");
        rpcMethodFilter.setPrefWidth(120);
        Button clearButton = new Button("清空");
        clearButton.setOnAction(e -> rpcRows.clear());
        Button exportButton = new Button("导出 CSV…");
        exportButton.setOnAction(e -> exportCsv("cwmp-rpc",
                List.of("time", "deviceId", "direction", "method", "status", "details", "session"),
                rpcRows, r -> List.of(r.time(), r.deviceId(), r.direction(), r.method(), r.status(),
                        r.details(), r.session())));
        HBox toolbar = new HBox(8, rpcDeviceFilter, rpcMethodFilter, pauseCheck,
                clearButton, exportButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        BorderPane pane = new BorderPane(table);
        pane.setTop(toolbar);
        return new Tab("CWMP RPC", pane);
    }

    private Tab buildXmppTab() {
        TableView<XmppRow> table = new TableView<>(filteredXmpp);
        table.getColumns().setAll(
                col("时间", 110, XmppRow::time),
                col("设备 ID", 220, XmppRow::deviceId),
                col("事件", 260, XmppRow::event),
                col("详情", 400, XmppRow::details));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("暂无 XMPP 事件"));

        xmppDeviceFilter.setPromptText("设备过滤…");
        xmppDeviceFilter.setPrefWidth(160);
        Button clearButton = new Button("清空");
        clearButton.setOnAction(e -> xmppRows.clear());
        Button exportButton = new Button("导出 CSV…");
        exportButton.setOnAction(e -> exportCsv("xmpp-events",
                List.of("time", "deviceId", "event", "details"),
                xmppRows, r -> List.of(r.time(), r.deviceId(), r.event(), r.details())));
        HBox toolbar = new HBox(8, xmppDeviceFilter, clearButton, exportButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        BorderPane pane = new BorderPane(table);
        pane.setTop(toolbar);
        return new Tab("XMPP 事件", pane);
    }

    private TableColumn<RpcRow, String> statusCol() {
        TableColumn<RpcRow, String> col = col("状态", 130, RpcRow::status);
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if (item.contains("FAIL") || item.contains("ERROR") || item.contains("UNSUPPORTED")) {
                    setStyle("-fx-text-fill: #cf222e;");
                } else if ("SUCCESS".equals(item) || "HANDLED".equals(item) || "RECEIVED".equals(item)) {
                    setStyle("-fx-text-fill: #1a7f37;");
                } else {
                    setStyle("");
                }
            }
        });
        return col;
    }

    private <T> TableColumn<T, String> col(String title, double width, Function<T, String> mapper) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new SimpleStringProperty(mapper.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private void onBatch(List<SimEvent> batch) {
        if (pauseCheck.isSelected()) {
            return;
        }
        String rpcDevice = filterText(rpcDeviceFilter);
        String rpcMethod = filterText(rpcMethodFilter);
        String xmppDevice = filterText(xmppDeviceFilter);
        for (SimEvent event : batch) {
            switch (event.category()) {
                case CWMP_RPC -> {
                    if (matches(event.deviceId(), rpcDevice)
                            && matches(event.attr("method"), rpcMethod)) {
                        appendCapped(rpcRows, new RpcRow(
                                TIME_FORMAT.format(event.timestamp()),
                                event.deviceId(),
                                event.attr("sessionId"),
                                event.attr("direction"),
                                event.attr("method"),
                                event.attr("status"),
                                event.attr("details")));
                    }
                }
                case XMPP_EVENT, CONNECTION_STATE -> {
                    if (matches(event.deviceId(), xmppDevice)) {
                        appendCapped(xmppRows, new XmppRow(
                                TIME_FORMAT.format(event.timestamp()),
                                event.deviceId(),
                                event.type(),
                                event.category() == SimEvent.Category.CONNECTION_STATE
                                        ? event.attr("reason") : event.attr("details")));
                    }
                }
                default -> {
                    // DEVICE_STATE 由设备表格轮询覆盖
                }
            }
        }
    }

    private static String filterText(TextField field) {
        return field.getText() == null ? "" : field.getText().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String value, String filterLower) {
        return filterLower.isEmpty()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(filterLower));
    }

    private static <T> void appendCapped(ObservableList<T> list, T row) {
        if (list.size() >= BUFFER_LIMIT) {
            list.remove(0);
        }
        list.add(row);
    }

    private static String abbreviate(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return sessionId == null ? "" : sessionId;
        }
        return sessionId.substring(0, 8);
    }

    private <T> void exportCsv(String baseName, List<String> header, List<T> rows,
                               Function<T, List<String>> mapper) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出 CSV");
        chooser.setInitialFileName(baseName + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.println(String.join(",", header));
            for (T row : rows) {
                writer.println(mapper.apply(row).stream()
                        .map(MonitorView::csvEscape)
                        .reduce((a, b) -> a + "," + b).orElse(""));
            }
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "导出失败：" + e.getMessage()).showAndWait();
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ---------------- 全局搜索（ViewSearcher） ----------------

    @Override
    public int applySearch(String query, SearchOptions options) {
        globalQuery = query == null ? "" : query.trim();
        globalOptions = options != null ? options : SearchOptions.DEFAULT;
        filteredRpc.setPredicate(this::matchesGlobal);
        filteredXmpp.setPredicate(this::matchesGlobal);
        return filteredRpc.size() + filteredXmpp.size();
    }

    @Override
    public void clearSearch() {
        globalQuery = "";
        filteredRpc.setPredicate(r -> true);
        filteredXmpp.setPredicate(r -> true);
    }

    private boolean matchesGlobal(RpcRow row) {
        return globalQuery.isEmpty()
                || globalOptions.matches(globalQuery, row.deviceId())
                || globalOptions.matches(globalQuery, row.method())
                || globalOptions.matches(globalQuery, row.status())
                || globalOptions.matches(globalQuery, row.direction())
                || globalOptions.matches(globalQuery, row.details());
    }

    private boolean matchesGlobal(XmppRow row) {
        return globalQuery.isEmpty()
                || globalOptions.matches(globalQuery, row.deviceId())
                || globalOptions.matches(globalQuery, row.event())
                || globalOptions.matches(globalQuery, row.details());
    }

    @Override
    public void dispose() {
        eventBridge.removeHandler(batchHandler);
    }
}
