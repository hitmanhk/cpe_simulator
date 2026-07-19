package com.cpe.simulator.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Standard reference documents (TR-098 / TR-181 / TR-369) for the protocol
 * guide. Each document is a scrollable card stack: overview, key facts table,
 * structural notes and the relationship to this simulator.
 */
final class ProtocolStandards {

    record Doc(String navTitle, String heading, String overview,
               List<String[]> facts, String structureTitle, List<String> structurePoints,
               String simulatorNote, String searchText) {
    }

    private ProtocolStandards() {
    }

    static List<Doc> all() {
        return List.of(tr098(), tr181(), tr369());
    }

    static javafx.scene.Node render(Doc doc) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        // 概述卡片
        Label heading = new Label(doc.heading());
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label overview = new Label(doc.overview());
        overview.setWrapText(true);
        overview.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 13px;");
        VBox overviewCard = new VBox(8, heading, overview);
        overviewCard.getStyleClass().add("card");
        overviewCard.setPadding(new Insets(16));

        // 关键信息卡片
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(8);
        int row = 0;
        for (String[] fact : doc.facts()) {
            Label key = new Label(fact[0]);
            key.setStyle("-fx-text-fill: -color-fg-muted;");
            Label value = new Label(fact[1]);
            value.setWrapText(true);
            grid.add(key, 0, row);
            grid.add(value, 1, row);
            row++;
        }
        VBox factsCard = new VBox(10, sectionTitle("关键信息"), grid);
        factsCard.getStyleClass().add("card");
        factsCard.setPadding(new Insets(16));

        // 结构要点卡片
        VBox points = new VBox(6);
        for (String point : doc.structurePoints()) {
            Label item = new Label("• " + point);
            item.setWrapText(true);
            item.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 13px;");
            points.getChildren().add(item);
        }
        VBox structureCard = new VBox(10, sectionTitle(doc.structureTitle()), points);
        structureCard.getStyleClass().add("card");
        structureCard.setPadding(new Insets(16));

        // 与本模拟器的关系
        Label note = new Label(doc.simulatorNote());
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: -color-fg-default; -fx-font-size: 13px;");
        VBox noteCard = new VBox(10, sectionTitle("本模拟器的支持情况"), note);
        noteCard.getStyleClass().add("card");
        noteCard.setPadding(new Insets(16));

        content.getChildren().addAll(overviewCard, factsCard, structureCard, noteCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        return scroll;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        return label;
    }

    /** TR-098：Internet Gateway Device 数据模型。 */
    private static Doc tr098() {
        String search = "TR-098 Internet Gateway Device Data Model InternetGatewayDevice 网关 数据模型 LANDevice WANDevice WLANConfiguration";
        return new Doc("TR-098 数据模型", "TR-098 — Internet Gateway Device Data Model",
                "TR-098 是宽带论坛面向互联网网关设备（IGD：家用路由器、ONT 等）制定的 TR-069 数据模型标准，"
                        + "定义了设备通过 CWMP 被 ACS 管理时的完整参数树，是早期 CPE 远程管理的事实标准。",
                List.of(
                        new String[]{"标准编号", "Broadband Forum TR-098"},
                        new String[]{"全称", "Internet Gateway Device Data Model for TR-069"},
                        new String[]{"发布组织", "Broadband Forum（宽带论坛）"},
                        new String[]{"首版时间", "2008 年（Issue 1），后续多个 Amendment 修订"},
                        new String[]{"根对象", "InternetGatewayDevice."},
                        new String[]{"适用设备", "互联网网关设备（家用路由器、ONT/光猫）"},
                        new String[]{"后继标准", "TR-181（Device Data Model，面向所有设备类型）"}),
                "结构特点",
                List.of(
                        "层级较深：LAN 配置在 InternetGatewayDevice.LANDevice.1.* 下，"
                                + "如 LANEthernetInterfaceConfig.1.、WLANConfiguration.1.",
                        "WiFi 一体化：SSID、加密、信道等集中在 LANDevice.1.WLANConfiguration.{i}. 表",
                        "WAN 分层：WANDevice.1.WANConnectionDevice.1.WANIPConnection.1. 三层嵌套",
                        "XMPP 为 Amendment 增补：InternetGatewayDevice.XMPP.Connection.1.*（NAT 回连用）",
                        "语音业务在 Services.VoiceService.1.* 下（SIP 配置）"),
                "内置模型 models/tr098.yaml（94 个参数），覆盖 DeviceInfo、ManagementServer、GPON、"
                        + "WAN（多实例连接表）、WiFi、LAN、VOIP、XMPP 章节；"
                        + "在「配置」页将数据模型设为 tr098 即可使用。",
                search);
    }

    /** TR-181：Device 数据模型。 */
    private static Doc tr181() {
        String search = "TR-181 Device Data Model Device. 数据模型 WiFi SSID Radio AccessPoint AliasBasedAddressing";
        return new Doc("TR-181 数据模型", "TR-181 — Device Data Model for TR-069",
                "TR-181 是 TR-098 的现代化重构，把建模对象从“互联网网关”推广到所有托管设备"
                        + "（网关、ONT、机顶盒、IoT 设备等），是当前 TR-069 部署的主流数据模型，"
                        + "Issue 2 持续修订至今。",
                List.of(
                        new String[]{"标准编号", "Broadband Forum TR-181"},
                        new String[]{"全称", "Device Data Model for TR-069"},
                        new String[]{"发布组织", "Broadband Forum（宽带论坛）"},
                        new String[]{"首版时间", "2010 年（Issue 1）；Issue 2 自 2012 年起持续修订"},
                        new String[]{"根对象", "Device."},
                        new String[]{"适用设备", "所有托管设备（网关、ONT、机顶盒、IoT）"},
                        new String[]{"前身标准", "TR-098（Internet Gateway Device Data Model）"}),
                "与 TR-098 的主要差异",
                List.of(
                        "扁平化：WiFi 拆为 Device.WiFi.SSID.{i}.、WiFi.Radio.{i}.、"
                                + "WiFi.AccessPoint.{i}. 三张独立表，替代 WLANConfiguration 一体化表",
                        "以太网独立成章：Device.Ethernet.Interface.{i}.、Ethernet.Link.{i}.，"
                                + "不再挂在 LANDevice 深层",
                        "LAN 简化：Device.LAN.Interface.{i}.（IP 寻址）与以太网接口解耦",
                        "别名寻址：ManagementServer.AliasBasedAddressing，允许用 [Alias:xxx] 稳定引用实例",
                        "DeviceInfo 扩充：AdditionalHardwareVersion、FirstUseDate、DeviceLog 等",
                        "XMPP 位置：Device.XMPP.Connection.1.*（与 TR-098 路径不同，对接时注意）"),
                "内置模型 models/tr181.yaml（143 个参数），为默认模型（配置页 model=tr181），"
                        + "覆盖 DeviceInfo、ManagementServer、GPON、WAN、WiFi（SSID/Radio/AccessPoint）、"
                        + "LAN、Ethernet、VOIP、XMPP 章节。",
                search);
    }

    /** TR-369：USP。 */
    private static Doc tr369() {
        String search = "TR-369 USP User Services Platform Agent Controller MTP WebSocket STOMP MQTT ProtoBuf E2E Notify";
        return new Doc("TR-369 (USP)", "TR-369 — User Services Platform（用户服务平台）",
                "TR-369 是宽带论坛发布的下一代设备管理平台标准（USP），是 TR-069/CWMP 的官方继任者："
                        + "针对 TR-069 在 NAT 穿透、二进制效率、常驻会话、物联网规模上的不足重新设计。",
                List.of(
                        new String[]{"标准编号", "Broadband Forum TR-369"},
                        new String[]{"全称", "User Services Platform（USP）"},
                        new String[]{"发布组织", "Broadband Forum（宽带论坛）"},
                        new String[]{"首版时间", "2018 年（Issue 1）"},
                        new String[]{"定位", "TR-069（CWMP）的官方继任者"},
                        new String[]{"角色模型", "USP Agent（设备侧）↔ USP Controller（管理侧）"},
                        new String[]{"消息编码", "Protocol Buffers 二进制（替代 SOAP/XML）"},
                        new String[]{"传输层 MTP", "WebSocket / STOMP / MQTT（与消息层解耦，可并存多条）"},
                        new String[]{"数据模型", "Device:2（TR-181 Issue 2 的演进）"}),
                "与 TR-069 的关键差异",
                List.of(
                        "消息类型：Add / Set / Delete / Get / GetInstances / Operate / Notify，"
                                + "替代 CWMP RPC；通知含 OnBoardReq、ValueChange、Event、TransferComplete",
                        "常驻会话：WebSocket 长连接下 Controller 可随时下发，"
                                + "不再需要 Connection Request 回连机制（TR-069 的 HTTP/XMPP 回连成为历史）",
                        "E2E 会话上下文：端到端会话完整性保护，可跨 MTP 中继",
                        "双向发起：Controller 与 Agent 均可发起会话（TR-069 会话只能由 CPE 发起）",
                        "二进制高效：ProtoBuf 编码显著降低报文体积，适合大规模 IoT 部署"),
                "本模拟器不支持 USP：当前实现为 TR-069 CWMP（含 Amendment 6 的 XMPP 回连，"
                        + "用于 NAT 穿透场景），数据模型为 TR-098 / TR-181。"
                        + "如需 USP 验证，请使用支持 TR-369 的 Agent 实现。",
                search);
    }
}
