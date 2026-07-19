package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.DeviceSnapshot;
import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import com.cpe.simulator.engine.CpeDevice;
import com.cpe.simulator.engine.VendorProfile;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Device management: vendor-grouped tree table. Vendors are collapsible;
 * checkboxes support select-all (toolbar), select-group (vendor row) and
 * per-device selection. Filters cover vendor name/OUI/product class/
 * software/hardware version/connection-request host/ACS host.
 * 「新增设备」仅创建（CREATED）；「启动设备/停止设备」对勾选集生效。
 */
public class DevicesView extends AbstractView implements ViewSearcher {

    /** 树行：厂商行（统计信息）或设备行。 */
    record DeviceRow(boolean vendorRow, VendorProfile vendor, int total, int online,
                     DeviceSnapshot device) {
        static DeviceRow ofVendor(VendorProfile vendor, int total, int online) {
            return new DeviceRow(true, vendor, total, online, null);
        }

        static DeviceRow ofDevice(DeviceSnapshot device) {
            return new DeviceRow(false, null, 0, 0, device);
        }
    }

    private static final int POLL_MILLIS = 1000;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MainWindow mainWindow;
    private final TreeTableView<DeviceRow> tree = new TreeTableView<>();
    private final CheckBox selectAllBox = new CheckBox("全选");
    private final TextField searchField = new TextField();
    private final Label summaryLabel = new Label();

    // 高级过滤字段
    private final TextField ouiFilter = new TextField();
    private final TextField classFilter = new TextField();
    private final TextField swFilter = new TextField();
    private final TextField hwFilter = new TextField();
    private final TextField connIpFilter = new TextField();
    private final TextField acsIpFilter = new TextField();
    private final VBox advancedBar = buildAdvancedBar();

    /** 勾选状态（deviceId 集合），独立于树结构，重建不丢。 */
    private final Set<String> checkedIds = new LinkedHashSet<>();
    /** 展开的厂商名集合，重建后恢复。 */
    private final Set<String> expandedVendors = new HashSet<>();

    /** 全局搜索条（Ctrl+F）状态，与本地过滤叠加。 */
    private String globalQuery = "";
    private SearchOptions globalOptions = SearchOptions.DEFAULT;

    private Timeline pollTimeline;
    private String structureSignature = "";
    private boolean updatingSelectAll;

    public DevicesView(EngineController engineController, UiEventBridge eventBridge, MainWindow mainWindow) {
        super(engineController, eventBridge);
        this.mainWindow = mainWindow;

        Label title = new Label("设备管理");
        title.getStyleClass().add("view-title");

        getChildren().addAll(title, buildToolbar(), buildAdvancedBar(), buildTree(), buildSummaryBar());
    }

    // ---------------- 工具栏 ----------------

    private HBox buildToolbar() {
        selectAllBox.setOnAction(e -> toggleSelectAll(selectAllBox.isSelected()));

        searchField.setPromptText("搜索厂商名 / 设备 ID / 序列号…");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, o, n) -> rebuildTree());

        Button advancedButton = new Button("高级过滤", new FontIcon(Feather.FILTER));
        advancedButton.setOnAction(e -> toggleAdvancedBar());

        Button addVendorButton = new Button("新增厂商", new FontIcon(Feather.PLUS));
        addVendorButton.setOnAction(e -> addVendorDialog());
        Button addDeviceButton = new Button("新增设备", new FontIcon(Feather.PLUS_SQUARE));
        addDeviceButton.setOnAction(e -> addDeviceDialog());
        Button startDevicesButton = new Button("启动设备", new FontIcon(Feather.PLAY));
        startDevicesButton.getStyleClass().add("accent");
        startDevicesButton.setTooltip(new javafx.scene.control.Tooltip(
                "勾选设备上线：公网→ONLINE 并上报 0 BOOTSTRAP；NAT→XMPP 建联。需先「启动模拟器」"));
        startDevicesButton.setOnAction(e -> startCheckedDevices());
        Button stopDevicesButton = new Button("停止设备", new FontIcon(Feather.SQUARE));
        stopDevicesButton.setOnAction(e -> stopCheckedDevices());

        MenuItem informItem = new MenuItem("触发 Inform（2 PERIODIC）");
        informItem.setOnAction(e -> forChecked(d -> d.triggerInform("2 PERIODIC"), true));
        MenuItem rebootItem = new MenuItem("重启");
        rebootItem.setOnAction(e -> forChecked(d -> d.scheduleReboot(0), true));
        MenuItem factoryResetItem = new MenuItem("恢复出厂设置");
        factoryResetItem.setOnAction(e -> forChecked(d -> d.scheduleFactoryReset(0), true));
        MenuItem batchConfigItem = new MenuItem("批量配置 DeviceInfo…");
        batchConfigItem.setOnAction(e -> batchConfigDialog());
        MenuItem removeItem = new MenuItem("移除设备");
        removeItem.setOnAction(e -> removeCheckedDevices());
        MenuButton moreButton = new MenuButton("更多操作", new FontIcon(Feather.MORE_HORIZONTAL));
        moreButton.getItems().addAll(informItem, rebootItem, factoryResetItem,
                new javafx.scene.control.SeparatorMenuItem(), batchConfigItem, removeItem);

        HBox bar = new HBox(8, selectAllBox, searchField, advancedButton,
                new javafx.scene.control.Separator(),
                addVendorButton, addDeviceButton, startDevicesButton, stopDevicesButton, moreButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildAdvancedBar() {
        ouiFilter.setPromptText("OUI");
        classFilter.setPromptText("型号（ProductClass）");
        swFilter.setPromptText("软件版本");
        hwFilter.setPromptText("硬件版本");
        connIpFilter.setPromptText("回连 IP");
        acsIpFilter.setPromptText("ACS IP");
        List.of(ouiFilter, classFilter, swFilter, hwFilter, connIpFilter, acsIpFilter)
                .forEach(f -> {
                    f.setPrefWidth(130);
                    f.textProperty().addListener((obs, o, n) -> rebuildTree());
                });
        Button clearButton = new Button("清除过滤");
        clearButton.setOnAction(e -> {
            List.of(ouiFilter, classFilter, swFilter, hwFilter, connIpFilter, acsIpFilter)
                    .forEach(TextField::clear);
            searchField.clear();
        });

        HBox fields = new HBox(8, new Label("过滤:"), ouiFilter, classFilter,
                swFilter, hwFilter, connIpFilter, acsIpFilter, clearButton);
        fields.setAlignment(Pos.CENTER_LEFT);
        fields.setPadding(new Insets(6, 0, 0, 0));

        VBox bar = new VBox(fields);
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    private void toggleAdvancedBar() {
        advancedBar.setVisible(!advancedBar.isVisible());
        advancedBar.setManaged(advancedBar.isVisible());
    }

    // ---------------- 树表 ----------------

    @SuppressWarnings("unchecked")
    private TreeTableView<DeviceRow> buildTree() {
        TreeTableColumn<DeviceRow, String> checkCol = new TreeTableColumn<>("");
        checkCol.setPrefWidth(40);
        checkCol.setCellFactory(col -> new CheckCell());
        checkCol.setSortable(false);

        TreeTableColumn<DeviceRow, String> nameCol = textCol("厂商 / 设备", 280, row ->
                row.vendorRow() ? row.vendor().name() + "　（" + row.total() + " 台，在线 " + row.online() + "）"
                        : row.device().deviceId());
        nameCol.setCellFactory(col -> new NameCell());

        TreeTableColumn<DeviceRow, String> serialCol = textCol("序列号", 100, row ->
                row.vendorRow() ? "" : row.device().serial());
        TreeTableColumn<DeviceRow, String> swCol = textCol("软件版本", 90, row ->
                row.vendorRow() ? "" : row.device().softwareVersion());
        TreeTableColumn<DeviceRow, String> hwCol = textCol("硬件版本", 90, row ->
                row.vendorRow() ? "" : row.device().hardwareVersion());
        TreeTableColumn<DeviceRow, String> connTypeCol = textCol("建联类型", 80, row ->
                row.vendorRow() ? "" : row.device().connectionType());
        TreeTableColumn<DeviceRow, String> stateCol = textCol("建联状态", 110, row ->
                row.vendorRow() ? "" : stateText(row.device()));
        stateCol.setCellFactory(col -> new StateCell());
        TreeTableColumn<DeviceRow, String> onlineCol = textCol("在线时间", 100, row ->
                row.vendorRow() ? "" : onlineDuration(row.device().onlineSince()));
        TreeTableColumn<DeviceRow, String> informsCol = textCol("Inform 数", 80, row ->
                row.vendorRow() ? "" : String.valueOf(row.device().informCount()));
        TreeTableColumn<DeviceRow, String> lastInformCol = textCol("最近 Inform", 130, row ->
                row.vendorRow() || row.device().lastInformTime() == null ? ""
                        : TIME_FORMAT.format(row.device().lastInformTime()));

        tree.getColumns().setAll(checkCol, nameCol, serialCol, swCol, hwCol,
                connTypeCol, stateCol, onlineCol, informsCol, lastInformCol);
        tree.setShowRoot(false);
        tree.setRoot(new TreeItem<>());
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setPlaceholder(new Label("设备池为空——点击「新增厂商」或「新增设备」开始"));

        tree.setRowFactory(tv -> {
            TreeTableRow<DeviceRow> row = new TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && row.getItem() != null && !row.getItem().vendorRow()) {
                    openDetail(row.getItem().device().deviceId());
                }
            });
            row.itemProperty().addListener((obs, oldItem, newItem) ->
                    row.setContextMenu(newItem == null ? null : buildContextMenu(newItem)));
            return row;
        });

        VBox.setVgrow(tree, Priority.ALWAYS);
        return tree;
    }

    private HBox buildSummaryBar() {
        summaryLabel.setStyle("-fx-text-fill: -color-fg-muted;");
        return new HBox(summaryLabel);
    }

    private TreeTableColumn<DeviceRow, String> textCol(String title, double width,
                                                       Function<DeviceRow, String> mapper) {
        TreeTableColumn<DeviceRow, String> col = new TreeTableColumn<>(title);
        col.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue() != null && cell.getValue().getValue() != null
                        ? mapper.apply(cell.getValue().getValue()) : ""));
        col.setPrefWidth(width);
        return col;
    }

    // ---------------- 单元格 ----------------

    private class CheckCell extends TreeTableCell<DeviceRow, String> {
        private final CheckBox checkBox = new CheckBox();

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            DeviceRow row = getTableRow().getItem();
            checkBox.setOnAction(null);
            if (row.vendorRow()) {
                List<String> groupIds = deviceIdsOfVendor(row.vendor().name());
                boolean all = !groupIds.isEmpty() && checkedIds.containsAll(groupIds);
                boolean any = groupIds.stream().anyMatch(checkedIds::contains);
                checkBox.setSelected(all);
                checkBox.setIndeterminate(any && !all);
                checkBox.setOnAction(e -> toggleVendorGroup(row.vendor().name(), checkBox.isSelected()));
            } else {
                checkBox.setSelected(checkedIds.contains(row.device().deviceId()));
                checkBox.setIndeterminate(false);
                checkBox.setOnAction(e -> {
                    if (checkBox.isSelected()) {
                        checkedIds.add(row.device().deviceId());
                    } else {
                        checkedIds.remove(row.device().deviceId());
                    }
                    refreshChecks();
                });
            }
            setGraphic(checkBox);
        }
    }

    private class NameCell extends TreeTableCell<DeviceRow, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                return;
            }
            DeviceRow row = getTableRow().getItem();
            setText(item);
            if (row.vendorRow()) {
                setGraphic(new FontIcon(Feather.FOLDER));
                setStyle("-fx-font-weight: bold;");
            } else {
                setGraphic(new FontIcon(Feather.CPU));
                setStyle("");
            }
        }
    }

    private class StateCell extends TreeTableCell<DeviceRow, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null
                    || getTableRow().getItem().vendorRow()) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item);
            DeviceSnapshot device = getTableRow().getItem().device();
            setStyle(switch (device.state()) {
                case "ONLINE" -> "-fx-text-fill: #1a7f37; -fx-font-weight: bold;";
                case "CONNECTING" -> "-fx-text-fill: #bf8700;";
                case "OFFLINE" -> "-fx-text-fill: #cf222e;";
                default -> "-fx-text-fill: -color-fg-muted;";
            });
        }
    }

    private static String stateText(DeviceSnapshot device) {
        return switch (device.state()) {
            case "ONLINE" -> "在线" + (device.sessionActive() ? " ⚡" : "");
            case "CONNECTING" -> "建联中";
            case "OFFLINE" -> "离线";
            default -> "未启动";
        };
    }

    private static String onlineDuration(Instant onlineSince) {
        if (onlineSince == null) {
            return "-";
        }
        long seconds = java.time.Duration.between(onlineSince, Instant.now()).getSeconds();
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? h + "h " + m + "m" : (m > 0 ? m + "m " + s + "s" : s + "s");
    }

    // ---------------- 右键菜单 ----------------

    private ContextMenu buildContextMenu(DeviceRow row) {
        if (row.vendorRow()) {
            MenuItem selectGroup = new MenuItem("全选本组");
            selectGroup.setOnAction(e -> toggleVendorGroup(row.vendor().name(), true));
            MenuItem editVendor = new MenuItem("编辑厂商…");
            editVendor.setOnAction(e -> editVendorDialog(row.vendor()));
            MenuItem removeVendor = new MenuItem("删除厂商");
            removeVendor.setOnAction(e -> removeVendor(row.vendor().name()));
            return new ContextMenu(selectGroup, editVendor, removeVendor);
        }
        String deviceId = row.device().deviceId();
        if (!checkedIds.contains(deviceId)) {
            checkedIds.add(deviceId);
            refreshChecks();
        }
        MenuItem detail = new MenuItem("查看详情");
        detail.setOnAction(e -> openDetail(deviceId));
        MenuItem start = new MenuItem("启动设备");
        start.setOnAction(e -> startCheckedDevices());
        MenuItem stop = new MenuItem("停止设备");
        stop.setOnAction(e -> stopCheckedDevices());
        MenuItem inform = new MenuItem("触发 Inform");
        inform.setOnAction(e -> forChecked(d -> d.triggerInform("2 PERIODIC"), true));
        MenuItem remove = new MenuItem("移除设备");
        remove.setOnAction(e -> removeCheckedDevices());
        return new ContextMenu(detail, start, stop, inform,
                new javafx.scene.control.SeparatorMenuItem(), remove);
    }

    // ---------------- 数据刷新 ----------------

    public void startPolling() {
        if (pollTimeline == null) {
            pollTimeline = new Timeline(new KeyFrame(Duration.millis(POLL_MILLIS), e -> poll()));
            pollTimeline.setCycleCount(Animation.INDEFINITE);
        }
        if (pollTimeline.getStatus() != Animation.Status.RUNNING) {
            pollTimeline.play();
        }
        poll();
    }

    @Override
    public void dispose() {
        if (pollTimeline != null) {
            pollTimeline.stop();
            pollTimeline = null;
        }
    }

    private void poll() {
        List<DeviceSnapshot> snapshots = new ArrayList<>();
        for (CpeDevice device : engineController.getDevices()) {
            snapshots.add(DeviceSnapshot.of(device));
        }
        updateTree(snapshots);
    }

    /** 结构签名（厂商集合 + 设备 ID 集合）变化才重建树，否则仅更新行数据。 */
    private void updateTree(List<DeviceSnapshot> snapshots) {
        StringBuilder signature = new StringBuilder();
        engineController.getVendors().forEach(v -> signature.append(v.name()).append(';'));
        snapshots.forEach(d -> signature.append(d.deviceId()).append(';'));
        String newSignature = signature.toString();
        if (!newSignature.equals(structureSignature)) {
            structureSignature = newSignature;
            rebuildTreeInternal(snapshots);
            return;
        }
        updateRowValues(snapshots);
        updateSummary(snapshots);
    }

    private void updateRowValues(List<DeviceSnapshot> snapshots) {
        Map<String, DeviceSnapshot> byId = new LinkedHashMap<>();
        snapshots.forEach(d -> byId.put(d.deviceId(), d));
        Map<String, long[]> vendorStats = new LinkedHashMap<>();
        snapshots.forEach(d -> {
            long[] stats = vendorStats.computeIfAbsent(
                    d.vendorName() != null ? d.vendorName() : "", k -> new long[2]);
            stats[0]++;
            if ("ONLINE".equals(d.state())) {
                stats[1]++;
            }
        });
        for (TreeItem<DeviceRow> vendorItem : tree.getRoot().getChildren()) {
            DeviceRow vendorRow = vendorItem.getValue();
            if (vendorRow == null || !vendorRow.vendorRow()) {
                continue;
            }
            long[] stats = vendorStats.getOrDefault(vendorRow.vendor().name(), new long[2]);
            vendorItem.setValue(DeviceRow.ofVendor(vendorRow.vendor(), (int) stats[0], (int) stats[1]));
            for (TreeItem<DeviceRow> child : vendorItem.getChildren()) {
                DeviceRow childRow = child.getValue();
                if (childRow != null && !childRow.vendorRow()) {
                    DeviceSnapshot fresh = byId.get(childRow.device().deviceId());
                    if (fresh != null) {
                        child.setValue(DeviceRow.ofDevice(fresh));
                    }
                }
            }
        }
    }

    /** 重建整棵树（保留勾选集与展开状态）。 */
    private void rebuildTree() {
        List<DeviceSnapshot> snapshots = new ArrayList<>();
        for (CpeDevice device : engineController.getDevices()) {
            snapshots.add(DeviceSnapshot.of(device));
        }
        rebuildTreeInternal(snapshots);
    }

    private void rebuildTreeInternal(List<DeviceSnapshot> snapshots) {
        recordExpandedState();
        Map<String, List<DeviceSnapshot>> byVendor = groupByVendor(snapshots);

        TreeItem<DeviceRow> root = new TreeItem<>();
        root.setExpanded(true);
        for (VendorProfile vendor : engineController.getVendors()) {
            List<DeviceSnapshot> devices = byVendor.getOrDefault(vendor.name(), List.of());
            List<DeviceSnapshot> visible = devices.stream().filter(this::matchesFilters).toList();
            if (visible.isEmpty() && !vendorMatchesSearch(vendor)) {
                continue;
            }
            int online = (int) devices.stream().filter(d -> "ONLINE".equals(d.state())).count();
            TreeItem<DeviceRow> vendorItem = new TreeItem<>(
                    DeviceRow.ofVendor(vendor, devices.size(), online));
            for (DeviceSnapshot snapshot : visible) {
                vendorItem.getChildren().add(new TreeItem<>(DeviceRow.ofDevice(snapshot)));
            }
            vendorItem.setExpanded(expandedVendors.isEmpty() || expandedVendors.contains(vendor.name()));
            root.getChildren().add(vendorItem);
        }
        tree.setRoot(root);
        structureSignature = "";
        updateSummary(snapshots);
        refreshChecks();
    }

    private void recordExpandedState() {
        expandedVendors.clear();
        if (tree.getRoot() == null) {
            return;
        }
        for (TreeItem<DeviceRow> child : tree.getRoot().getChildren()) {
            if (child.getValue() != null && child.getValue().vendorRow() && child.isExpanded()) {
                expandedVendors.add(child.getValue().vendor().name());
            }
        }
    }

    private Map<String, List<DeviceSnapshot>> groupByVendor(List<DeviceSnapshot> snapshots) {
        Map<String, List<DeviceSnapshot>> map = new LinkedHashMap<>();
        for (DeviceSnapshot snapshot : snapshots) {
            map.computeIfAbsent(snapshot.vendorName() != null
                    ? snapshot.vendorName() : VendorProfile.DEFAULT_VENDOR_NAME,
                    k -> new ArrayList<>()).add(snapshot);
        }
        return map;
    }

    private void updateSummary(List<DeviceSnapshot> snapshots) {
        long online = snapshots.stream().filter(d -> "ONLINE".equals(d.state())).count();
        summaryLabel.setText(String.format("共 %d 个厂商、%d 台设备（在线 %d），已勾选 %d 台",
                engineController.getVendors().size(), snapshots.size(), online, checkedIds.size()));
    }

    // ---------------- 过滤与全局搜索 ----------------

    private boolean matchesFilters(DeviceSnapshot d) {
        String query = lower(searchField.getText());
        if (!query.isEmpty()) {
            boolean matches = contains(d.deviceId(), query) || contains(d.serial(), query)
                    || contains(d.vendorName(), query);
            if (!matches) {
                return false;
            }
        }
        if (!globalQuery.isEmpty()) {
            boolean matches = globalOptions.matches(globalQuery, d.deviceId())
                    || globalOptions.matches(globalQuery, d.serial())
                    || globalOptions.matches(globalQuery, d.vendorName());
            if (!matches) {
                return false;
            }
        }
        return matchesField(d.oui(), ouiFilter) && matchesField(d.productClass(), classFilter)
                && matchesField(d.softwareVersion(), swFilter) && matchesField(d.hardwareVersion(), hwFilter)
                && matchesField(d.connReqHost(), connIpFilter) && matchesField(d.acsHost(), acsIpFilter);
    }

    private boolean vendorMatchesSearch(VendorProfile vendor) {
        String query = lower(searchField.getText());
        return !query.isEmpty() && contains(vendor.name(), query);
    }

    private static boolean matchesField(String value, TextField filter) {
        String f = lower(filter.getText());
        return f.isEmpty() || contains(value, f);
    }

    private static boolean contains(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public int applySearch(String query, SearchOptions options) {
        globalQuery = query == null ? "" : query.trim();
        globalOptions = options != null ? options : SearchOptions.DEFAULT;
        rebuildTree();
        return countVisible();
    }

    @Override
    public void clearSearch() {
        globalQuery = "";
        rebuildTree();
    }

    private int countVisible() {
        int count = 0;
        if (tree.getRoot() == null) {
            return 0;
        }
        for (TreeItem<DeviceRow> vendorItem : tree.getRoot().getChildren()) {
            if (vendorItem.getValue() != null && vendorItem.getValue().vendorRow()) {
                count += vendorItem.getChildren().size();
            }
        }
        return count;
    }

    // ---------------- 勾选操作 ----------------

    private void toggleSelectAll(boolean select) {
        if (updatingSelectAll) {
            return;
        }
        if (select) {
            for (CpeDevice device : engineController.getDevices()) {
                checkedIds.add(device.getDeviceId());
            }
        } else {
            checkedIds.clear();
        }
        refreshChecks();
    }

    private void toggleVendorGroup(String vendorName, boolean select) {
        List<String> groupIds = deviceIdsOfVendor(vendorName);
        if (select) {
            checkedIds.addAll(groupIds);
        } else {
            checkedIds.removeAll(groupIds);
        }
        refreshChecks();
    }

    private List<String> deviceIdsOfVendor(String vendorName) {
        List<String> ids = new ArrayList<>();
        for (CpeDevice device : engineController.getDevices()) {
            if (vendorName.equals(device.getVendorName())) {
                ids.add(device.getDeviceId());
            }
        }
        return ids;
    }

    /** 勾选状态变化后刷新全选框与单元格显示。 */
    private void refreshChecks() {
        int total = engineController.getDevices().size();
        updatingSelectAll = true;
        selectAllBox.setSelected(total > 0 && checkedIds.size() >= total);
        selectAllBox.setIndeterminate(!checkedIds.isEmpty() && checkedIds.size() < total);
        updatingSelectAll = false;
        tree.refresh();
        updateSummary(engineController.getDevices().stream().map(DeviceSnapshot::of).toList());
    }

    // ---------------- 对话框与设备操作 ----------------

    private void addVendorDialog() {
        mainWindow.syncPendingConfig();
        VendorDialog dialog = new VendorDialog(null);
        dialog.showAndWait().ifPresent(vendor -> {
            if (!VendorDialog.isValidOui(vendor.oui())) {
                new Alert(Alert.AlertType.WARNING,
                        "OUI 必须是 6 位十六进制字符，当前：" + vendor.oui()).showAndWait();
                return;
            }
            engineController.addVendor(vendor);
            rebuildTree();
        });
    }

    private void editVendorDialog(VendorProfile vendor) {
        VendorDialog dialog = new VendorDialog(vendor);
        dialog.showAndWait().ifPresent(updated -> {
            if (!VendorDialog.isValidOui(updated.oui())) {
                new Alert(Alert.AlertType.WARNING, "OUI 必须是 6 位十六进制字符").showAndWait();
                return;
            }
            engineController.addVendor(updated); // 注册或替换
            rebuildTree();
        });
    }

    private void removeVendor(String name) {
        String error = engineController.removeVendor(name);
        if (error != null) {
            new Alert(Alert.AlertType.INFORMATION, error).showAndWait();
            return;
        }
        rebuildTree();
    }

    private void addDeviceDialog() {
        mainWindow.syncPendingConfig();
        List<String> vendorNames = engineController.getVendors().stream()
                .map(VendorProfile::name).toList();
        if (vendorNames.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先新增厂商").showAndWait();
            return;
        }
        boolean tr181 = !"tr098".equalsIgnoreCase(engineController.getPendingConfig().getModel());
        AddDeviceDialog dialog = new AddDeviceDialog(vendorNames, tr181);
        dialog.showAndWait().ifPresent(request ->
                Thread.ofVirtual().name("device-add").start(() -> {
                    try {
                        engineController.addDevices(request.vendorName(), request.count(),
                                request.deviceInfoOverrides());
                        Platform.runLater(this::rebuildTree);
                    } catch (Throwable t) {
                        showErrorAsync("新增设备失败", t);
                    }
                }));
    }

    private void startCheckedDevices() {
        if (checkedIds.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先勾选设备").showAndWait();
            return;
        }
        if (engineController.getState() != EngineController.EngineState.RUNNING) {
            new Alert(Alert.AlertType.INFORMATION,
                    "模拟器未启动。请先点击顶部「启动模拟器」（启动基础设施），再启动设备。").showAndWait();
            return;
        }
        List<String> ids = List.copyOf(checkedIds);
        Thread.ofVirtual().name("device-start").start(() -> {
            try {
                engineController.startDevices(ids);
            } catch (Throwable t) {
                showErrorAsync("启动设备失败", t);
            }
        });
    }

    private void stopCheckedDevices() {
        if (checkedIds.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先勾选设备").showAndWait();
            return;
        }
        List<String> ids = List.copyOf(checkedIds);
        Thread.ofVirtual().name("device-stop").start(() -> engineController.stopDevices(ids));
    }

    private void forChecked(Consumer<CpeDevice> action, boolean requireRunning) {
        if (checkedIds.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先勾选设备").showAndWait();
            return;
        }
        if (requireRunning && engineController.getState() != EngineController.EngineState.RUNNING) {
            new Alert(Alert.AlertType.INFORMATION, "模拟器未启动，请先点击顶部「启动模拟器」").showAndWait();
            return;
        }
        List<String> ids = List.copyOf(checkedIds);
        Thread.ofVirtual().name("device-action").start(() -> {
            for (String id : ids) {
                CpeDevice device = findDevice(id);
                if (device != null) {
                    try {
                        action.accept(device);
                    } catch (Throwable t) {
                        showErrorAsync("设备操作失败：" + id, t);
                    }
                }
            }
        });
    }

    private void batchConfigDialog() {
        if (checkedIds.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先勾选设备").showAndWait();
            return;
        }
        boolean tr181 = !"tr098".equalsIgnoreCase(engineController.getPendingConfig().getModel());
        BatchConfigDialog dialog = new BatchConfigDialog(checkedIds.size(), tr181);
        dialog.showAndWait().ifPresent(values ->
                Thread.ofVirtual().name("device-batch-config").start(() -> {
                    int success = 0;
                    int failed = 0;
                    for (String id : checkedIds) {
                        CpeDevice device = findDevice(id);
                        if (device == null) {
                            failed++;
                            continue;
                        }
                        try {
                            values.forEach((path, value) -> device.getDataModel().setRelativeValue(path, value));
                            success++;
                        } catch (Throwable t) {
                            failed++;
                        }
                    }
                    int finalSuccess = success;
                    int finalFailed = failed;
                    Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION,
                            "批量配置完成：成功 " + finalSuccess + " 台"
                                    + (finalFailed > 0 ? "，失败 " + finalFailed + " 台" : "")).show());
                }));
    }

    private void removeCheckedDevices() {
        if (checkedIds.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "请先勾选设备").showAndWait();
            return;
        }
        List<String> ids = List.copyOf(checkedIds);
        Thread.ofVirtual().name("device-remove").start(() -> {
            engineController.removeDevices(ids);
            checkedIds.removeAll(ids);
            Platform.runLater(this::rebuildTree);
        });
    }

    private CpeDevice findDevice(String deviceId) {
        for (CpeDevice device : engineController.getDevices()) {
            if (device.getDeviceId().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }

    /** Selects and scrolls to the given device (expanding its vendor group). */
    public void focusDevice(String deviceId) {
        if (tree.getRoot() == null) {
            return;
        }
        for (TreeItem<DeviceRow> vendorItem : tree.getRoot().getChildren()) {
            for (TreeItem<DeviceRow> child : vendorItem.getChildren()) {
                DeviceRow row = child.getValue();
                if (row != null && !row.vendorRow() && row.device().deviceId().equals(deviceId)) {
                    vendorItem.setExpanded(true);
                    tree.getSelectionModel().select(child);
                    tree.scrollTo(tree.getRow(child));
                    return;
                }
            }
        }
    }

    private void openDetail(String deviceId) {
        CpeDevice device = findDevice(deviceId);
        if (device == null) {
            new Alert(Alert.AlertType.INFORMATION, "设备不存在：" + deviceId).showAndWait();
            return;
        }
        new DeviceDetailView(device).show();
    }

    static void showErrorAsync(String title, Throwable t) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR,
                title + "：" + (t.getMessage() != null ? t.getMessage() : t.toString())).show());
    }

    static String formatInstant(Instant instant) {
        return instant != null ? TIME_FORMAT.format(instant) : "-";
    }
}
