package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.cwmp.CwmpSession;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ParameterNode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Map;

/**
 * Device detail window: identity/session panel, action buttons and the full
 * TR-069 parameter tree with inline value editing (setValue + 4 VALUE CHANGE,
 * same semantics as the CLI {@code event value-change}).
 */
public class DeviceDetailView {

    private final CpeDevice device;
    private final Stage stage = new Stage();

    private final Label stateValue = new Label();
    private final Label informCountValue = new Label();
    private final Label lastInformValue = new Label();
    private final Label sessionValue = new Label();
    private final Label xmppValue = new Label();
    private final Label vendorValue = new Label();
    private final Label connTypeValue = new Label();
    private final Label onlineSinceValue = new Label();

    private final TreeTableView<ParameterNode> paramTree = new TreeTableView<>();
    private Timeline refreshTimeline;

    public DeviceDetailView(CpeDevice device) {
        this.device = device;
        stage.setTitle("设备详情 — " + device.getDeviceId());

        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        root.setCenter(buildParamTree());

        stage.setScene(new Scene(root, 980, 700));
        stage.setOnHidden(e -> stopRefresh());
        refreshInfo();
        startRefresh();
    }

    public void show() {
        stage.show();
    }

    private VBox buildTop() {
        GridPane info = new GridPane();
        info.setHgap(24);
        info.setVgap(6);
        info.setPadding(new Insets(14));
        addInfoRow(info, 0, "设备 ID", device.getDeviceId(), "序列号", device.getSerialNumber());
        addInfoRow(info, 1, "OUI", device.getOui(), "Product Class", device.getProductClass());
        info.add(infoLabel("所属厂商"), 0, 2);
        info.add(vendorValue, 1, 2);
        info.add(infoLabel("建联类型"), 2, 2);
        info.add(connTypeValue, 3, 2);
        info.add(infoLabel("状态"), 0, 3);
        info.add(stateValue, 1, 3);
        info.add(infoLabel("在线时间"), 2, 3);
        info.add(onlineSinceValue, 3, 3);
        info.add(infoLabel("Inform 数"), 0, 4);
        info.add(informCountValue, 1, 4);
        info.add(infoLabel("最近 Inform"), 2, 4);
        info.add(lastInformValue, 3, 4);
        info.add(infoLabel("CWMP 会话"), 0, 5);
        info.add(sessionValue, 1, 5);
        info.add(infoLabel("XMPP"), 2, 5);
        info.add(xmppValue, 3, 5);

        Button informButton = new Button("触发 Inform");
        informButton.setOnAction(e -> runAsync("触发 Inform", () -> device.triggerInform("2 PERIODIC")));
        Button rebootButton = new Button("重启");
        rebootButton.setOnAction(e -> runAsync("重启", () -> device.scheduleReboot(0)));
        Button factoryResetButton = new Button("恢复出厂设置");
        factoryResetButton.setOnAction(e -> runAsync("恢复出厂设置", () -> device.scheduleFactoryReset(0)));
        Button refreshButton = new Button("刷新参数树");
        refreshButton.setOnAction(e -> rebuildTree());

        HBox actions = new HBox(8, informButton, rebootButton, factoryResetButton, refreshButton);
        actions.setPadding(new Insets(0, 14, 10, 14));

        return new VBox(info, actions);
    }

    private Label infoLabel(String text) {
        Label label = new Label(text + "：");
        label.setStyle("-fx-text-fill: -color-fg-muted;");
        return label;
    }

    private void addInfoRow(GridPane grid, int row, String k1, String v1, String k2, String v2) {
        grid.add(infoLabel(k1), 0, row);
        grid.add(new Label(v1), 1, row);
        grid.add(infoLabel(k2), 2, row);
        grid.add(new Label(v2), 3, row);
    }

    @SuppressWarnings("unchecked")
    private TreeTableView<ParameterNode> buildParamTree() {
        TreeTableColumn<ParameterNode, String> nameCol = new TreeTableColumn<>("参数");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getValue().getName()));
        nameCol.setPrefWidth(340);

        TreeTableColumn<ParameterNode, String> valueCol = new TreeTableColumn<>("值");
        valueCol.setCellValueFactory(cell -> {
            ParameterNode node = cell.getValue().getValue();
            return new SimpleStringProperty(node.getValue() != null ? node.getValue().getValue() : "");
        });
        valueCol.setPrefWidth(320);

        TreeTableColumn<ParameterNode, String> typeCol = new TreeTableColumn<>("类型");
        typeCol.setCellValueFactory(cell -> {
            ParameterNode node = cell.getValue().getValue();
            return new SimpleStringProperty(node.getValue() != null ? node.getValue().getType() : "");
        });
        typeCol.setPrefWidth(120);

        TreeTableColumn<ParameterNode, String> writableCol = new TreeTableColumn<>("可写");
        writableCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getValue().isWritable() ? "是" : ""));
        writableCol.setPrefWidth(60);

        TreeTableColumn<ParameterNode, String> notificationCol = new TreeTableColumn<>("Notification");
        notificationCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getValue().getNotification())));
        notificationCol.setPrefWidth(90);

        paramTree.getColumns().setAll(nameCol, valueCol, typeCol, writableCol, notificationCol);
        paramTree.setShowRoot(true);
        rebuildTree();

        paramTree.setRowFactory(tv -> {
            TreeTableRow<ParameterNode> row = new TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editValue(row.getTreeItem().getValue());
                }
            });
            MenuItem copyPath = new MenuItem("复制参数路径");
            copyPath.setOnAction(e -> {
                ParameterNode node = row.getTreeItem() != null ? row.getTreeItem().getValue() : null;
                if (node != null) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(node.getFullPath());
                    Clipboard.getSystemClipboard().setContent(content);
                }
            });
            MenuItem edit = new MenuItem("修改值并上报…");
            edit.setOnAction(e -> {
                if (!row.isEmpty()) {
                    editValue(row.getTreeItem().getValue());
                }
            });
            row.setContextMenu(new ContextMenu(copyPath, edit));
            return row;
        });

        VBox.setVgrow(paramTree, Priority.ALWAYS);
        BorderPane.setMargin(paramTree, new Insets(0, 10, 10, 10));
        return paramTree;
    }

    private void rebuildTree() {
        DataModel model = device.getDataModel();
        if (model == null) {
            return;
        }
        ParameterNode root = model.getRoot();
        TreeItem<ParameterNode> rootItem = buildItem(root);
        rootItem.setExpanded(true);
        paramTree.setRoot(rootItem);
    }

    private TreeItem<ParameterNode> buildItem(ParameterNode node) {
        TreeItem<ParameterNode> item = new TreeItem<>(node);
        for (Map.Entry<String, ParameterNode> child : node.getChildren().entrySet()) {
            item.getChildren().add(buildItem(child.getValue()));
        }
        return item;
    }

    private void editValue(ParameterNode node) {
        if (node == null || !node.isLeaf()) {
            return;
        }
        if (!node.isWritable()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "参数只读，无法修改：\n" + node.getFullPath()).showAndWait();
            return;
        }
        TextInputDialog dialog = new TextInputDialog(
                node.getValue() != null ? node.getValue().getValue() : "");
        dialog.setTitle("修改参数值");
        dialog.setHeaderText(node.getFullPath()
                + "\n类型：" + (node.getValue() != null ? node.getValue().getType() : "xsd:string")
                + "\n保存后触发 4 VALUE CHANGE Inform");
        dialog.setContentText("新值：");
        dialog.showAndWait().ifPresent(value -> runAsync("修改参数", () -> {
            device.getDataModel().setValue(node.getFullPath(), value);
            device.triggerInform("4 VALUE CHANGE");
        }));
    }

    private void refreshInfo() {
        vendorValue.setText(device.getVendorName() != null ? device.getVendorName() : "（未归属厂商）");
        connTypeValue.setText(device.getConnectionType());
        stateValue.setText(device.getState().name());
        onlineSinceValue.setText(device.getOnlineSince() != null
                ? DevicesView.formatInstant(device.getOnlineSince()) : "-");
        informCountValue.setText(String.valueOf(device.getInformCount()));
        lastInformValue.setText(DevicesView.formatInstant(device.getLastInformTime()));
        CwmpSession session = device.getSession();
        if (session != null && session.getState() != CwmpSession.State.IDLE) {
            sessionValue.setText(session.getState() + "（" + session.getSessionId() + "）");
        } else {
            sessionValue.setText("空闲");
        }
        if (device.getXmppConnection() != null) {
            var conn = device.getXmppConnection();
            xmppValue.setText(conn.getState() + (conn.getJid() != null ? "（" + conn.getJid() + "）" : ""));
        } else {
            xmppValue.setText(device.getConfig().isNatMode() ? "未连接" : "公网模式不适用");
        }
    }

    private void startRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshInfo()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private void runAsync(String actionName, Runnable action) {
        Thread.ofVirtual().name("device-detail-action").start(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                DevicesView.showErrorAsync(actionName + "失败", t);
            }
        });
    }
}
