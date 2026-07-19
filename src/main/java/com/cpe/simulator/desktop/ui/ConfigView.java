package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConfigLoader;
import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration editor: grouped form covering every AppConfig option.
 * Every field carries a plain-language tooltip (purpose / valid range /
 * default), and passwords are shown in plain text — this is a self-use
 * simulator, not a production credential store. YAML load/save is fully
 * compatible with the CLI config file format.
 */
public class ConfigView extends AbstractView implements ViewSearcher {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    // 基本设置
    private final ComboBox<String> modeCombo = new ComboBox<>(
            FXCollections.observableArrayList("public", "nat"));
    private final TextField countField = new TextField();
    private final TextField acsUrlField = new TextField();
    private final TextField acsUserField = new TextField();
    private final TextField acsPasswordField = new TextField();
    private final ComboBox<String> modelCombo = new ComboBox<>(
            FXCollections.observableArrayList("tr181", "tr098"));
    private final TextField ouiField = new TextField();
    private final TextField productClassField = new TextField();
    private final TextField serialPrefixField = new TextField();
    private final TextField serialStartField = new TextField();

    // Inform 调度
    private final TextField bootstrapIntervalField = new TextField();
    private final TextField bootstrapJitterField = new TextField();
    private final TextField bootIntervalField = new TextField();
    private final TextField bootInformJitterField = new TextField();
    private final TextField periodicIntervalField = new TextField();
    private final TextField periodicJitterField = new TextField();
    private final CheckBox xmppOnlyCheck = new CheckBox("仅 XMPP 模式（xmpp-only）");
    private final TextField maxSessionsField = new TextField();
    private final TextField sessionTimeoutField = new TextField();

    // 批量连接
    private final TextField connectBatchField = new TextField();
    private final TextField connectBatchDelayField = new TextField();
    private final TextField bootBatchSizeField = new TextField();
    private final TextField bootBatchDelayField = new TextField();
    private final TextField bootJitterField = new TextField();

    // 回连设置
    private final TextField connReqHostField = new TextField();
    private final TextField connReqPortField = new TextField();
    private final TextField connReqUserField = new TextField();
    private final TextField connReqPasswordField = new TextField();

    // 模拟行为
    private final TextField rebootDelayField = new TextField();
    private final TextField downloadDelayField = new TextField();
    private final TextField downloadSuccessRateField = new TextField();

    // XMPP
    private final TextField xmppHostField = new TextField();
    private final TextField xmppPortField = new TextField();
    private final TextField xmppDomainField = new TextField();
    private final TextField xmppUserPrefixField = new TextField();
    private final TextField xmppPasswordField = new TextField();
    private final TextField xmppResourceField = new TextField();
    private final ComboBox<String> tlsModeCombo = new ComboBox<>(
            FXCollections.observableArrayList("NONE", "DIRECT", "STARTTLS"));
    private final ComboBox<String> saslCombo = new ComboBox<>(
            FXCollections.observableArrayList("SCRAM-SHA-1", "SCRAM-SHA-256", "PLAIN"));
    private final TextField handshakeTimeoutField = new TextField();
    private final TextField keepaliveIntervalField = new TextField();
    private final TextField keepaliveTimeoutField = new TextField();
    private final TextField reconnectInitialDelayField = new TextField();
    private final TextField reconnectMaxDelayField = new TextField();

    // 日志
    private final ComboBox<String> logLevelCombo = new ComboBox<>(
            FXCollections.observableArrayList("DEBUG", "INFO", "WARN", "ERROR"));
    private final TextField deviceFilterField = new TextField();

    private final java.util.List<javafx.scene.Node> editableControls = new java.util.ArrayList<>();
    private final java.util.List<Label> fieldLabels = new java.util.ArrayList<>();
    private final java.util.List<Label> hintLabels = new java.util.ArrayList<>();

    public ConfigView(EngineController engineController, UiEventBridge eventBridge) {
        super(engineController, eventBridge);

        Label title = new Label("配置");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("所有配置项与 CLI 的 application.yaml 完全对应；鼠标悬停任意输入框可查看详细说明。引擎运行期间配置锁定。");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        subtitle.setWrapText(true);

        Button loadButton = new Button("加载 YAML…");
        loadButton.setOnAction(e -> loadYaml());
        Button saveButton = new Button("保存 YAML…");
        saveButton.setOnAction(e -> saveYaml());
        Button defaultsButton = new Button("恢复默认");
        defaultsButton.setOnAction(e -> populate(ConfigLoader.loadDefault()));
        HBox actions = new HBox(8, loadButton, saveButton, defaultsButton);

        javafx.scene.layout.VBox groups = new javafx.scene.layout.VBox(10,
                group("基本设置", basicGrid()),
                group("Inform 调度（定时上报）", informGrid()),
                group("批量连接限速", batchGrid()),
                group("回连设置（Connection Request）", connReqGrid()),
                group("模拟行为", behaviorGrid()),
                group("XMPP（NAT 模式）", xmppGrid()),
                group("日志", loggingGrid()));

        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        javafx.scene.layout.VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(title, subtitle, actions, scroll);

        editableControls.addAll(java.util.List.of(
                modeCombo, countField, acsUrlField, acsUserField, acsPasswordField, modelCombo,
                ouiField, productClassField, serialPrefixField, serialStartField,
                bootstrapIntervalField, bootstrapJitterField, bootIntervalField, bootInformJitterField,
                periodicIntervalField, periodicJitterField, xmppOnlyCheck, maxSessionsField,
                sessionTimeoutField, connectBatchField, connectBatchDelayField, bootBatchSizeField,
                bootBatchDelayField, bootJitterField, connReqHostField, connReqPortField,
                connReqUserField, connReqPasswordField, rebootDelayField, downloadDelayField,
                downloadSuccessRateField, xmppHostField, xmppPortField, xmppDomainField,
                xmppUserPrefixField, xmppPasswordField, xmppResourceField, tlsModeCombo, saslCombo,
                handshakeTimeoutField, keepaliveIntervalField, keepaliveTimeoutField,
                reconnectInitialDelayField, reconnectMaxDelayField, logLevelCombo, deviceFilterField,
                loadButton, defaultsButton));

        populate(ConfigLoader.loadDefault());
    }

    private TitledPane group(String title, GridPane grid) {
        TitledPane pane = new TitledPane(title, grid);
        pane.setCollapsible(false);
        return pane;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints labelCol = new ColumnConstraints(210);
        ColumnConstraints fieldCol = new ColumnConstraints(300);
        ColumnConstraints hintCol = new ColumnConstraints(420);
        grid.getColumnConstraints().addAll(labelCol, fieldCol, hintCol);
        return grid;
    }

    private void row(GridPane grid, int row, String label, Control field, String hint, String tooltip) {
        Label labelNode = new Label(label);
        fieldLabels.add(labelNode);
        grid.add(labelNode, 0, row);
        grid.add(field, 1, row);
        if (hint != null) {
            Label h = new Label(hint);
            h.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
            h.setWrapText(true);
            hintLabels.add(h);
            grid.add(h, 2, row);
        }
        if (tooltip != null) {
            Tooltip tip = new Tooltip(tooltip);
            tip.setWrapText(true);
            tip.setMaxWidth(420);
            tip.setShowDelay(javafx.util.Duration.millis(150));
            field.setTooltip(tip);
        }
    }

    // ---------------- 全局搜索（ViewSearcher） ----------------

    @Override
    public int applySearch(String query, SearchOptions options) {
        clearSearch();
        if (query == null || query.isBlank()) {
            return 0;
        }
        SearchOptions opts = options != null ? options : SearchOptions.DEFAULT;
        String q = query.trim();
        int matches = 0;
        for (Label label : fieldLabels) {
            if (opts.matches(q, label.getText())) {
                label.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 4;");
                matches++;
            }
        }
        for (Label hint : hintLabels) {
            if (opts.matches(q, hint.getText())) {
                hint.setStyle("-fx-text-fill: -color-accent-emphasis; -fx-font-size: 11px; "
                        + "-fx-background-color: -color-accent-subtle; -fx-background-radius: 4;");
                matches++;
            }
        }
        return matches;
    }

    @Override
    public void clearSearch() {
        for (Label label : fieldLabels) {
            label.setStyle("");
        }
        for (Label hint : hintLabels) {
            hint.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        }
    }

    private GridPane basicGrid() {
        GridPane g = grid();
        row(g, 0, "运行模式 (mode)", modeCombo, "public=公网回连 / nat=XMPP 穿透",
                "设备与 ACS 的拓扑关系：\n"
                        + "• public：设备拥有公网可达地址，ACS 通过 HTTP 直接回连设备（回连设置生效）\n"
                        + "• nat：设备位于 NAT 之后，ACS 经 XMPP 服务器推送回连请求（需配置 XMPP 分组）\n"
                        + "默认：public");
        row(g, 1, "设备数量 (count)", countField, "仅 CLI start 使用；GUI 经设备管理页新增",
                "CLI 模式启动时创建的 CPE 实例数量（GUI 模式忽略此项——「启动模拟器」仅启动基础设施，"
                        + "设备在设备管理页通过「新增设备」创建）。内存参考：TR-181 每设备约 12KB。默认：100");
        row(g, 2, "ACS 地址 (acs-url)", acsUrlField, "设备 Inform 上报的目的地",
                "ACS 的 CWMP 端点 URL，所有 Inform 与 RPC 响应都通过 HTTP POST 发往该地址。\n"
                        + "示例：http://acs.example.com:7547/\n默认：http://localhost:7547/");
        row(g, 3, "ACS 用户名 (acs-user)", acsUserField, "ACS 侧 HTTP 认证",
                "设备访问 ACS 时的 HTTP 认证用户名；自动协商 Digest（MD5/SHA-256）或 Basic。默认：admin");
        row(g, 4, "ACS 密码 (acs-password)", acsPasswordField, "明文显示，自用模拟器",
                "设备访问 ACS 时的 HTTP 认证密码。明文显示（本工具为自用模拟器，密码仅用于对接测试 ACS）。默认：admin");
        modelCombo.setEditable(true);
        row(g, 5, "数据模型 (model)", modelCombo, "tr181 / tr098 / 自定义 YAML 路径",
                "设备参数树模型：\n• tr181：Device. 根（TR-181，143 个参数）\n"
                        + "• tr098：InternetGatewayDevice. 根（TR-098，94 个参数）\n"
                        + "• 或直接输入自定义模型 YAML 文件路径（格式见 models/tr181.yaml）\n默认：tr181");
        row(g, 6, "OUI", ouiField, "6 位十六进制，DeviceId 组成部分",
                "厂商唯一标识（6 位十六进制），与 ProductClass、序列号共同构成 TR-069 DeviceId。默认：000000");
        row(g, 7, "Product Class", productClassField, "产品类别，DeviceId 组成部分",
                "设备产品类别字符串，构成 DeviceId 的第二部分。默认：SimCPE");
        row(g, 8, "序列号前缀 (serial-prefix)", serialPrefixField, "实际序列号 = 前缀 + 序号",
                "设备序列号前缀，第 N 台设备的序列号为「前缀 + (serial-start + N)」，如 SIM00001。默认：SIM");
        row(g, 9, "序列号起始 (serial-start)", serialStartField, "首台设备序号",
                "序列号起始序号，用于多实例错开编号段。默认：0");
        return g;
    }

    private GridPane informGrid() {
        GridPane g = grid();
        row(g, 0, "BOOTSTRAP 间隔（秒）", bootstrapIntervalField, "0 = 禁用",
                "0 BOOTSTRAP 事件的周期上报间隔（秒）。BOOTSTRAP 表示设备首次（或恢复出厂后）入网，"
                        + "通常只需一次，间隔给大值即可。默认：86400");
        row(g, 1, "BOOTSTRAP 初始抖动", bootstrapJitterField, "0.0 ~ 1.0",
                "首次调度的随机抖动比例：实际首次延迟 = 间隔 × (1 ± 抖动×随机)，"
                        + "用于把大批量设备的首次上报打散，避免惊群。范围 0.0~1.0。默认：0.2");
        row(g, 2, "BOOT 间隔（秒）", bootIntervalField, "0 = 禁用",
                "1 BOOT 事件的周期上报间隔（秒），0 表示禁用。默认：3600");
        row(g, 3, "BOOT 初始抖动", bootInformJitterField, "0.0 ~ 1.0",
                "BOOT 首次调度抖动比例，范围 0.0~1.0。默认：0.2");
        row(g, 4, "PERIODIC 间隔（秒）", periodicIntervalField, "0 = 禁用",
                "2 PERIODIC 周期 Inform 的上报间隔（秒），压测时的主要负载来源。默认：300");
        row(g, 5, "PERIODIC 初始抖动", periodicJitterField, "0.0 ~ 1.0",
                "PERIODIC 首次调度抖动比例，范围 0.0~1.0。默认：0.3");
        row(g, 6, "最大并发会话 (max-sessions)", maxSessionsField, "全局会话信号量",
                "全局最大并发 CWMP 会话数（信号量限流）：同一时刻最多 N 台设备处于会话中，"
                        + "超出排队等待（30 秒等不到则放弃本次）。调大增加 ACS 瞬时压力。默认：1000");
        row(g, 7, "会话超时（秒）", sessionTimeoutField, "会话无活动超时",
                "CWMP 会话无消息活动的超时时间（秒），超时后强制关闭会话。默认：30");
        xmppOnlyCheck.setTooltip(new Tooltip(
                "仅 XMPP 模式：跳过 HTTP 回连监听与启动 BOOTSTRAP，"
                        + "每台设备 XMPP 上线后才启动其周期 Inform。仅 NAT 模式可用，用于验证 XMPP 通道连通性。默认：关闭"));
        g.add(xmppOnlyCheck, 1, 8);
        return g;
    }

    private GridPane batchGrid() {
        GridPane g = grid();
        row(g, 0, "连接批大小 (connect-batch)", connectBatchField, "每批发起连接的设备数",
                "启动时每批发起连接的设备数量，控制建连风暴。默认：1000");
        row(g, 1, "连接批间延迟（毫秒）", connectBatchDelayField, "两批之间的间隔",
                "相邻两个连接批次之间的等待毫秒数。有效建连速率 ≈ 批大小 ÷ 批间延迟。默认：200");
        row(g, 2, "启动批大小 (boot-batch-size)", bootBatchSizeField, "每批 BOOTSTRAP 设备数",
                "启动时每批触发 0 BOOTSTRAP Inform 的设备数，用于阶梯式放量保护 ACS。默认：100");
        row(g, 3, "启动批间延迟（毫秒）", bootBatchDelayField, "两批之间的间隔",
                "相邻 BOOTSTRAP 批次之间的等待毫秒数（默认速率 ≈ 100 台/秒）。默认：1000");
        row(g, 4, "启动抖动 (boot-jitter)", bootJitterField, "批内随机延迟比例",
                "批内每台设备再叠加 0 ~（批间延迟 × 抖动）毫秒的随机延迟，进一步打散上报时点。范围 0.0~1.0。默认：0.5");
        return g;
    }

    private GridPane connReqGrid() {
        GridPane g = grid();
        row(g, 0, "实例地址 (conn-req-host)", connReqHostField, "出站源 IP + 回连监听 + 回连 URL 三合一",
                "本实例统一使用的网络地址，同时用于：\n• CWMP/XMPP 出站连接的源 IP\n"
                        + "• HTTP 回连服务的监听地址\n• 上报给 ACS 的 ConnectionRequestURL 地址\n"
                        + "多网卡跑多个模拟器实例时，为每个实例配置不同地址即可共用同一端口。默认：0.0.0.0（全部网卡）");
        row(g, 1, "回连端口 (conn-req-port)", connReqPortField, "⚠ 避开 Windows 保留端口段",
                "HTTP 回连服务监听端口（仅公网模式使用）。\n"
                        + "⚠ Windows 的 Hyper-V/WSL 会保留大段端口（netsh interface ipv4 show excludedportrange "
                        + "protocol=tcp 查看），例如 7457-7556 段——7547 恰好落在其中，绑定会报 "
                        + "“Address already in use”。遇到该错误请改用 17547 等空闲端口。默认：7547");
        row(g, 2, "回连用户名 (conn-req-user)", connReqUserField, "留空 = 不启用认证",
                "HTTP 回连 Basic 认证用户名；用户名与密码都非空才启用认证（ACS 回连时需带凭据）。默认：空");
        row(g, 3, "回连密码 (conn-req-password)", connReqPasswordField, "明文显示",
                "HTTP 回连 Basic 认证密码（明文显示）。默认：空");
        return g;
    }

    private GridPane behaviorGrid() {
        GridPane g = grid();
        row(g, 0, "重启耗时（秒）", rebootDelayField, "Reboot RPC 的模拟延迟",
                "收到 ACS 的 Reboot 指令后，设备离线等待该秒数再恢复上线并上报 1 BOOT。默认：5");
        row(g, 1, "下载耗时（秒）", downloadDelayField, "Download RPC 的模拟延迟",
                "收到固件下载（Download）指令后，模拟下载过程耗时，之后按成功率上报 TransferComplete。默认：10");
        row(g, 2, "下载成功率", downloadSuccessRateField, "0.0 ~ 1.0",
                "固件下载的成功概率（0.0~1.0），用于验证 ACS 的失败处理与重试逻辑。默认：0.99");
        return g;
    }

    private GridPane xmppGrid() {
        GridPane g = grid();
        row(g, 0, "服务器 (host)", xmppHostField, "XMPP 服务器地址",
                "XMPP 服务器主机名或 IP。NAT 模式必填。默认：localhost");
        row(g, 1, "端口 (port)", xmppPortField, "留空 = 按 TLS 模式自动",
                "XMPP 端口，留空自动选择：DIRECT TLS 用 5223，NONE/STARTTLS 用 5222。默认：空（自动）");
        row(g, 2, "域 (domain)", xmppDomainField, "XMPP 域（JID 组成部分）",
                "XMPP 服务域名，设备 JID 形如「用户名@域/资源」。NAT 模式必填。默认：iot.example.com");
        row(g, 3, "用户名前缀 (user-prefix)", xmppUserPrefixField, "xmpp-only 模式的用户名来源",
                "XMPP 用户名前缀（xmpp-only 模式使用）；普通 NAT 模式凭据由 ACS 通过 "
                        + "SetParameterValues 下发到 Device.XMPP.Connection.1.*。默认：cpe");
        row(g, 4, "密码 (password)", xmppPasswordField, "明文显示，NAT 模式必填",
                "XMPP 账号密码（明文显示）。NAT 模式必填。默认：secret");
        row(g, 5, "资源 (resource)", xmppResourceField, "JID 的资源部分",
                "XMPP 资源绑定名，JID 形如 用户名@域/资源。默认：sim");
        row(g, 6, "TLS 模式 (tls-mode)", tlsModeCombo, "通道加密方式",
                "• NONE：明文\n• DIRECT：连接即 TLS（默认端口 5223）\n"
                        + "• STARTTLS：明文协商后升级 TLS（默认端口 5222）\n"
                        + "模拟器信任所有服务器证书，便于对接自签名环境。默认：NONE");
        row(g, 7, "SASL 机制 (sasl-mechanism)", saslCombo, "认证算法",
                "优先使用的 SASL 认证机制；服务器不支持时按 PLAIN → SCRAM-SHA-256 → SCRAM-SHA-1 "
                        + "顺序回退选择双方都支持的机制。默认：SCRAM-SHA-1");
        row(g, 8, "握手超时（秒）", handshakeTimeoutField, "0 或负数 = 禁用",
                "XMPP 每个握手阶段（TCP/TLS/认证/绑定）的超时秒数，超时判定失败并重连。默认：60");
        row(g, 9, "保活间隔（秒）", keepaliveIntervalField, "XMPP Ping 周期",
                "上线后每该秒数发送一次 XMPP Ping（urn:xmpp:ping）探测连接活性。默认：60");
        row(g, 10, "保活超时（秒）", keepaliveTimeoutField, "等 pong 的超时",
                "发送 Ping 后等待 pong 的秒数，超时判定断线并触发重连。默认：30");
        row(g, 11, "重连初始延迟（秒）", reconnectInitialDelayField, "指数退避起点",
                "断线重连的指数退避初始延迟（秒），每次失败翻倍并叠加 ±10% 抖动。默认：30");
        row(g, 12, "重连最大延迟（秒）", reconnectMaxDelayField, "退避封顶",
                "指数退避的封顶延迟（秒）。默认：1800");
        return g;
    }

    private GridPane loggingGrid() {
        GridPane g = grid();
        row(g, 0, "日志级别 (level)", logLevelCombo, "同时影响 GUI 日志页与文件日志",
                "日志级别：DEBUG（含设备级详细轨迹）/ INFO / WARN / ERROR。"
                        + "日志写入 logs/cpe-simulator.log 并实时显示在「日志」页。默认：INFO");
        row(g, 1, "设备过滤 (device-filter)", deviceFilterField, "逗号分隔，留空 = 全部",
                "只输出指定设备 ID（逗号分隔）的日志，留空记录全部设备；"
                        + "不影响 GUI 各页面的数据展示。默认：空");
        return g;
    }

    /** Fills the form from the given config. */
    public void populate(AppConfig config) {
        modeCombo.setValue(config.getMode());
        countField.setText(String.valueOf(config.getCount()));
        acsUrlField.setText(config.getAcsUrl());
        acsUserField.setText(config.getAcsUser());
        acsPasswordField.setText(config.getAcsPassword());
        modelCombo.setValue(config.getModel());
        ouiField.setText(config.getOui());
        productClassField.setText(config.getProductClass());
        serialPrefixField.setText(config.getSerialPrefix());
        serialStartField.setText(String.valueOf(config.getSerialStart()));

        AppConfig.InformSchedules schedules = config.getInformSchedules();
        bootstrapIntervalField.setText(String.valueOf(schedules.getBootstrap().getInterval()));
        bootstrapJitterField.setText(String.valueOf(schedules.getBootstrap().getInitialJitter()));
        bootIntervalField.setText(String.valueOf(schedules.getBoot().getInterval()));
        bootInformJitterField.setText(String.valueOf(schedules.getBoot().getInitialJitter()));
        periodicIntervalField.setText(String.valueOf(schedules.getPeriodic().getInterval()));
        periodicJitterField.setText(String.valueOf(schedules.getPeriodic().getInitialJitter()));
        xmppOnlyCheck.setSelected(config.isXmppOnly());
        maxSessionsField.setText(String.valueOf(config.getMaxSessions()));
        sessionTimeoutField.setText(String.valueOf(config.getSessionTimeout()));

        connectBatchField.setText(String.valueOf(config.getConnectBatch()));
        connectBatchDelayField.setText(String.valueOf(config.getConnectBatchDelay()));
        bootBatchSizeField.setText(String.valueOf(config.getBootBatchSize()));
        bootBatchDelayField.setText(String.valueOf(config.getBootBatchDelay()));
        bootJitterField.setText(String.valueOf(config.getBootJitter()));

        connReqHostField.setText(config.getConnReqHost());
        connReqPortField.setText(String.valueOf(config.getConnReqPort()));
        connReqUserField.setText(config.getConnReqUser() != null ? config.getConnReqUser() : "");
        connReqPasswordField.setText(config.getConnReqPassword() != null ? config.getConnReqPassword() : "");

        rebootDelayField.setText(String.valueOf(config.getRebootDelay()));
        downloadDelayField.setText(String.valueOf(config.getDownloadDelay()));
        downloadSuccessRateField.setText(String.valueOf(config.getDownloadSuccessRate()));

        AppConfig.XmppConfig xmpp = config.getXmpp();
        xmppHostField.setText(xmpp.getHost());
        xmppPortField.setText(""); // 留空 = 按 TLS 模式自动选择端口
        xmppDomainField.setText(xmpp.getDomain());
        xmppUserPrefixField.setText(xmpp.getUserPrefix());
        xmppPasswordField.setText(xmpp.getPassword());
        xmppResourceField.setText(xmpp.getResource());
        tlsModeCombo.setValue(xmpp.getTlsMode().name());
        saslCombo.setValue(xmpp.getSaslMechanism());
        handshakeTimeoutField.setText(String.valueOf(xmpp.getHandshakeTimeout()));
        keepaliveIntervalField.setText(String.valueOf(xmpp.getKeepaliveInterval()));
        keepaliveTimeoutField.setText(String.valueOf(xmpp.getKeepaliveTimeout()));
        reconnectInitialDelayField.setText(String.valueOf(xmpp.getReconnectInitialDelay()));
        reconnectMaxDelayField.setText(String.valueOf(xmpp.getReconnectMaxDelay()));

        logLevelCombo.setValue(config.getLogging().getLevel());
        deviceFilterField.setText(config.getLogging().getDeviceFilter());
    }

    /** Builds an AppConfig from the current form content. */
    public AppConfig buildConfig() {
        AppConfig config = new AppConfig();
        config.setMode(modeCombo.getValue());
        config.setCount(parseInt("设备数量", countField, 1, Integer.MAX_VALUE));
        config.setAcsUrl(acsUrlField.getText().trim());
        config.setAcsUser(acsUserField.getText().trim());
        config.setAcsPassword(acsPasswordField.getText());
        config.setModel(modelCombo.getValue() != null ? modelCombo.getValue().trim() : "tr181");
        config.setOui(ouiField.getText().trim());
        config.setProductClass(productClassField.getText().trim());
        config.setSerialPrefix(serialPrefixField.getText().trim());
        config.setSerialStart(parseInt("序列号起始", serialStartField, 0, Integer.MAX_VALUE));

        AppConfig.InformSchedules schedules = config.getInformSchedules();
        schedules.getBootstrap().setInterval(parseInt("BOOTSTRAP 间隔", bootstrapIntervalField, 0, Integer.MAX_VALUE));
        schedules.getBootstrap().setInitialJitter(parseDouble("BOOTSTRAP 抖动", bootstrapJitterField, 0.0, 1.0));
        schedules.getBoot().setInterval(parseInt("BOOT 间隔", bootIntervalField, 0, Integer.MAX_VALUE));
        schedules.getBoot().setInitialJitter(parseDouble("BOOT 抖动", bootInformJitterField, 0.0, 1.0));
        schedules.getPeriodic().setInterval(parseInt("PERIODIC 间隔", periodicIntervalField, 0, Integer.MAX_VALUE));
        schedules.getPeriodic().setInitialJitter(parseDouble("PERIODIC 抖动", periodicJitterField, 0.0, 1.0));
        config.setXmppOnly(xmppOnlyCheck.isSelected());
        config.setMaxSessions(parseInt("最大并发会话", maxSessionsField, 1, 1_000_000));
        config.setSessionTimeout(parseInt("会话超时", sessionTimeoutField, 1, Integer.MAX_VALUE));

        config.setConnectBatch(parseInt("连接批大小", connectBatchField, 1, Integer.MAX_VALUE));
        config.setConnectBatchDelay(parseInt("连接批间延迟", connectBatchDelayField, 0, Integer.MAX_VALUE));
        config.setBootBatchSize(parseInt("启动批大小", bootBatchSizeField, 1, Integer.MAX_VALUE));
        config.setBootBatchDelay(parseInt("启动批间延迟", bootBatchDelayField, 0, Integer.MAX_VALUE));
        config.setBootJitter(parseDouble("启动抖动", bootJitterField, 0.0, 1.0));

        config.setConnReqHost(connReqHostField.getText().trim());
        config.setConnReqPort(parseInt("回连端口", connReqPortField, 1, 65535));
        setIfNotBlank(connReqUserField.getText(), config::setConnReqUser);
        setIfNotBlank(connReqPasswordField.getText(), config::setConnReqPassword);

        config.setRebootDelay(parseInt("重启耗时", rebootDelayField, 0, Integer.MAX_VALUE));
        config.setDownloadDelay(parseInt("下载耗时", downloadDelayField, 0, Integer.MAX_VALUE));
        config.setDownloadSuccessRate(parseDouble("下载成功率", downloadSuccessRateField, 0.0, 1.0));

        AppConfig.XmppConfig xmpp = config.getXmpp();
        xmpp.setHost(xmppHostField.getText().trim());
        String portText = xmppPortField.getText().trim();
        if (!portText.isEmpty()) {
            xmpp.setPort(parseInt("XMPP 端口", xmppPortField, 1, 65535));
        }
        xmpp.setDomain(xmppDomainField.getText().trim());
        xmpp.setUserPrefix(xmppUserPrefixField.getText().trim());
        xmpp.setPassword(xmppPasswordField.getText());
        xmpp.setResource(xmppResourceField.getText().trim());
        xmpp.setTlsMode(tlsModeCombo.getValue());
        xmpp.setSaslMechanism(saslCombo.getValue());
        xmpp.setHandshakeTimeout(parseInt("握手超时", handshakeTimeoutField, -1, Integer.MAX_VALUE));
        xmpp.setKeepaliveInterval(parseInt("保活间隔", keepaliveIntervalField, 1, Integer.MAX_VALUE));
        xmpp.setKeepaliveTimeout(parseInt("保活超时", keepaliveTimeoutField, 1, Integer.MAX_VALUE));
        xmpp.setReconnectInitialDelay(parseInt("重连初始延迟", reconnectInitialDelayField, 0, Integer.MAX_VALUE));
        xmpp.setReconnectMaxDelay(parseInt("重连最大延迟", reconnectMaxDelayField, 1, Integer.MAX_VALUE));

        config.getLogging().setLevel(logLevelCombo.getValue());
        config.getLogging().setDeviceFilter(deviceFilterField.getText().trim());
        return config;
    }

    public void setEditingEnabled(boolean enabled) {
        editableControls.forEach(c -> c.setDisable(!enabled));
    }

    private void loadYaml() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("加载配置文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            populate(ConfigLoader.load(file.getAbsolutePath()));
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "加载配置失败：" + e.getMessage()).showAndWait();
        }
    }

    private void saveYaml() {
        AppConfig config;
        try {
            config = buildConfig();
        } catch (IllegalArgumentException e) {
            new Alert(Alert.AlertType.WARNING, "配置无效：" + e.getMessage()).showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存配置文件");
        chooser.setInitialFileName("application.yaml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            writeYaml(config, file);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "保存配置失败：" + e.getMessage()).showAndWait();
        }
    }

    /**
     * Writes config in the CLI-compatible layout: {@code simulator:} section
     * (with nested {@code logging:}) plus a top-level {@code xmpp:} section.
     * Null values and derived read-only properties are omitted; the XMPP port
     * is omitted when it matches the TLS-mode default so it stays "auto".
     */
    static void writeYaml(AppConfig config, File file) throws java.io.IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> simulatorMap = YAML.convertValue(config, Map.class);
        Object xmppNode = simulatorMap.remove("xmpp");
        simulatorMap.values().removeIf(java.util.Objects::isNull);
        simulatorMap.remove("natMode");
        simulatorMap.remove("connReqAuthEnabled");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("simulator", simulatorMap);
        if (xmppNode instanceof Map<?, ?> xmppMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> xmpp = (Map<String, Object>) xmppMap;
            xmpp.values().removeIf(java.util.Objects::isNull);
            xmpp.remove("tls");
            xmpp.remove("directTls");
            xmpp.remove("startTls");
            int defaultPort = config.getXmpp().isDirectTls() ? 5223 : 5222;
            if (Integer.valueOf(defaultPort).equals(xmpp.get("port"))) {
                xmpp.remove("port");
            }
            root.put("xmpp", xmpp);
        }
        YAML.writeValue(file, root);
    }

    private static void setIfNotBlank(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    private static int parseInt(String fieldName, TextField field, int min, int max) {
        String text = field.getText().trim();
        final int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是整数，当前值：" + text);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " 超出范围 [" + min + ", " + max + "]，当前值：" + value);
        }
        return value;
    }

    private static double parseDouble(String fieldName, TextField field, double min, double max) {
        String text = field.getText().trim();
        final double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是数字，当前值：" + text);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " 超出范围 [" + min + ", " + max + "]，当前值：" + value);
        }
        return value;
    }
}
