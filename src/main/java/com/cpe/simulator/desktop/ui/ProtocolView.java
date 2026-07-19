package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Protocol guide. Top-level tabs per protocol area; flow areas use a left
 * sidebar master-detail layout with grouped navigation. Sidebar entries wrap
 * either a sequence-diagram flow or a reference document, each carrying a
 * searchText used by the Ctrl+F in-page search.
 */
public class ProtocolView extends AbstractView implements ViewSearcher {

    /** 侧边栏导航项：标题 + 内容构建器 + 可搜索文本。 */
    private record NavEntry(String title, Supplier<Node> content, String searchText) {
    }

    private record NavGroup(String title, List<NavEntry> entries) {
    }

    /** 侧边栏按钮与导航项的关联（搜索显隐用）。 */
    private record SidebarItem(ToggleButton button, NavEntry entry, Label groupLabel) {
    }

    private final List<SidebarItem> sidebarItems = new ArrayList<>();

    public ProtocolView(EngineController engineController, UiEventBridge eventBridge) {
        super(engineController, eventBridge);

        Label title = new Label("协议指南");
        title.getStyleClass().add("view-title");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        List<ProtocolFlows.Flow> sessionOverview = new ArrayList<>();
        sessionOverview.add(ProtocolFlows.cwmpSession());
        tabs.getTabs().add(sidebarTab("TR-069 会话流程", List.of(
                new NavGroup("总览", flowEntries(sessionOverview)),
                new NavGroup("事件码", flowEntries(ProtocolEventFlows.all())))));

        tabs.getTabs().add(sidebarTab("TR-069 回连流程", List.of(
                new NavGroup("回连模式", flowEntries(List.of(
                        ProtocolFlows.connectionRequestHttp(),
                        ProtocolFlows.connectionRequestXmpp()))))));

        tabs.getTabs().add(sidebarTab("TR-069 RPC 流程", rpcGroups()));

        tabs.getTabs().add(errorCodesTab());

        tabs.getTabs().add(sidebarTab("XMPP 建联流程", List.of(
                new NavGroup("建联", flowEntries(List.of(ProtocolFlows.xmppHandshake()))),
                new NavGroup("TLS 流程", flowEntries(List.of(
                        ProtocolFlows.tlsDirect(), ProtocolFlows.tlsStartTls()))),
                new NavGroup("SASL 流程", flowEntries(List.of(
                        ProtocolFlows.saslPlain(), ProtocolFlows.scramSha1(),
                        ProtocolFlows.scramSha256()))))));

        tabs.getTabs().add(sidebarTab("协议标准", List.of(
                new NavGroup("Broadband Forum 标准", docEntries(ProtocolStandards.all())))));

        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().addAll(title, tabs);
    }

    // ---------------- 搜索（ViewSearcher） ----------------

    @Override
    public int applySearch(String query, SearchOptions options) {
        if (query == null || query.isBlank()) {
            clearSearch();
            return 0;
        }
        Pattern pattern = options.toPattern(query.trim());
        int matches = 0;
        for (SidebarItem item : sidebarItems) {
            boolean hit = pattern.matcher(item.entry().searchText()).find();
            item.button().setVisible(hit);
            item.button().setManaged(hit);
            if (hit) {
                matches++;
            }
        }
        // 组标题：组内无可见项时隐藏
        for (SidebarItem item : sidebarItems) {
            if (item.groupLabel() != null) {
                // 由下方统一计算
            }
        }
        updateGroupLabels();
        return matches;
    }

    @Override
    public void clearSearch() {
        for (SidebarItem item : sidebarItems) {
            item.button().setVisible(true);
            item.button().setManaged(true);
        }
        updateGroupLabels();
    }

    private void updateGroupLabels() {
        // 收集每个组标题下是否还有可见按钮
        java.util.Map<Label, Boolean> hasVisible = new java.util.IdentityHashMap<>();
        for (SidebarItem item : sidebarItems) {
            Label group = item.groupLabel();
            if (group == null) {
                continue;
            }
            hasVisible.merge(group, item.button().isVisible(), (a, b) -> a || b);
        }
        hasVisible.forEach((label, visible) -> {
            label.setVisible(visible);
            label.setManaged(visible);
        });
    }

    // ---------------- 侧边栏构建 ----------------

    private List<NavEntry> flowEntries(List<ProtocolFlows.Flow> flows) {
        List<NavEntry> entries = new ArrayList<>();
        for (ProtocolFlows.Flow flow : flows) {
            entries.add(new NavEntry(flow.tabTitle(), () -> buildFlowContent(flow), flowSearchText(flow)));
        }
        return entries;
    }

    private List<NavEntry> docEntries(List<ProtocolStandards.Doc> docs) {
        List<NavEntry> entries = new ArrayList<>();
        for (ProtocolStandards.Doc doc : docs) {
            entries.add(new NavEntry(doc.navTitle(), () -> ProtocolStandards.render(doc),
                    doc.searchText() + " " + doc.heading() + " " + doc.overview() + " " + doc.simulatorNote()));
        }
        return entries;
    }

    private List<NavGroup> rpcGroups() {
        List<NavGroup> groups = new ArrayList<>();
        for (ProtocolFlows.FlowGroup group : ProtocolRpcFlows.groups()) {
            groups.add(new NavGroup(group.title(), flowEntries(group.flows())));
        }
        return groups;
    }

    private static String flowSearchText(ProtocolFlows.Flow flow) {
        StringBuilder text = new StringBuilder()
                .append(flow.tabTitle()).append(' ')
                .append(flow.summary()).append(' ')
                .append(flow.startMarker()).append(' ')
                .append(flow.endMarker());
        for (SequenceDiagram.FlowMessage message : flow.messages()) {
            if (message.label() != null) {
                text.append(' ').append(message.label());
            }
            if (message.note() != null) {
                text.append(' ').append(message.note());
            }
        }
        return text.toString();
    }

    private Tab sidebarTab(String title, List<NavGroup> groups) {
        VBox sidebar = new VBox(4);
        sidebar.setPadding(new Insets(12, 8, 12, 12));
        sidebar.setPrefWidth(210);
        sidebar.setMinWidth(210);
        sidebar.getStyleClass().add("protocol-sidebar");

        StackPane content = new StackPane();
        content.setPadding(new Insets(0));

        ToggleGroup toggleGroup = new ToggleGroup();
        List<ToggleButton> buttons = new ArrayList<>();
        List<NavEntry> entries = new ArrayList<>();

        for (NavGroup group : groups) {
            Label groupLabel = new Label(group.title());
            groupLabel.getStyleClass().add("protocol-sidebar-group");
            groupLabel.setPadding(new Insets(10, 4, 2, 4));
            sidebar.getChildren().add(groupLabel);
            for (NavEntry entry : group.entries()) {
                ToggleButton button = new ToggleButton(entry.title());
                button.setToggleGroup(toggleGroup);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setAlignment(Pos.CENTER_LEFT);
                button.getStyleClass().add("protocol-nav-item");
                int index = entries.size();
                button.setOnAction(e -> {
                    if (!button.isSelected()) {
                        button.setSelected(true);
                        return;
                    }
                    showContent(content, entries.get(index));
                });
                buttons.add(button);
                entries.add(entry);
                sidebar.getChildren().add(button);
                sidebarItems.add(new SidebarItem(button, entry, groupLabel));
            }
        }

        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setStyle("-fx-background-color: transparent;");
        sidebarScroll.setMinWidth(216);
        sidebarScroll.setPrefWidth(216);

        HBox layout = new HBox(sidebarScroll, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        content.setMinWidth(0);
        VBox.setVgrow(layout, Priority.ALWAYS);

        if (!buttons.isEmpty()) {
            buttons.get(0).setSelected(true);
            content.getChildren().setAll(entries.get(0).content().get());
        }

        Tab tab = new Tab(title, layout);
        tab.setClosable(false);
        return tab;
    }

    private void showContent(StackPane content, NavEntry entry) {
        Node node = entry.content().get();
        content.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(160), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    /** 流程详情：概要卡片 + 时序图（可滚动）。 */
    private Node buildFlowContent(ProtocolFlows.Flow flow) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.getChildren().add(buildSummaryCard(flow));
        content.getChildren().add(new SequenceDiagram(flow.lifelines(), flow.messages()));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        return scroll;
    }

    /** 错误码页签：Fault 结构说明 + 错误码全表。 */
    private Tab errorCodesTab() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        Label intro = new Label(
                "CPE 的 RPC 处理错误通过 SOAP Fault 返回，结构为 <detail><cwmp:Fault><FaultCode>9005</FaultCode>"
                        + "<FaultString>…</FaultString></cwmp:Fault></detail>，承载在 HTTP 200 的响应体内（应用层错误）。"
                        + "注意与 HTTP 传输层状态码区分：401 = ACS/回连认证、204 = 会话正常结束、503 = 设备离线拒绝回连。");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 13px;");
        VBox introCard = new VBox(intro);
        introCard.getStyleClass().add("card");
        introCard.setPadding(new Insets(16));

        TableView<ProtocolErrorCodes.ErrorCode> table = new TableView<>();
        table.getColumns().setAll(
                column("错误码", 90, ProtocolErrorCodes.ErrorCode::code),
                column("名称", 250, ProtocolErrorCodes.ErrorCode::name),
                column("含义", 400, ProtocolErrorCodes.ErrorCode::meaning),
                column("本模拟器行为", 320, ProtocolErrorCodes.ErrorCode::simulatorBehavior));
        table.getItems().setAll(ProtocolErrorCodes.all());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(introCard, table);
        Tab tab = new Tab("TR-069 错误码", content);
        tab.setClosable(false);
        return tab;
    }

    private TableColumn<ProtocolErrorCodes.ErrorCode, String> column(
            String title, double width,
            java.util.function.Function<ProtocolErrorCodes.ErrorCode, String> mapper) {
        TableColumn<ProtocolErrorCodes.ErrorCode, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cell -> new SimpleStringProperty(mapper.apply(cell.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    /** 概要卡片：流程说明 + 开始/结束消息特征。 */
    private VBox buildSummaryCard(ProtocolFlows.Flow flow) {
        Label summary = new Label(flow.summary());
        summary.setWrapText(true);
        summary.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 13px;");

        HBox markers = new HBox(24,
                marker("▶ 开始特征", flow.startMarker(), "#1a7f37"),
                marker("■ 结束特征", flow.endMarker(), "#cf222e"));

        VBox card = new VBox(12, summary, markers);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private VBox marker(String title, String text, String color) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(520);
        textLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        return new VBox(4, titleLabel, textLabel);
    }
}
