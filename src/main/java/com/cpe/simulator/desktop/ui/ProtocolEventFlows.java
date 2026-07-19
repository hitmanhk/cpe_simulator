package com.cpe.simulator.desktop.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-event-code CWMP session flows for the protocol guide's second-level
 * tabs. Each event documents trigger source, simulator behavior and what the
 * ACS is expected to do, plus a minimal sequence sketch.
 */
final class ProtocolEventFlows {

    private ProtocolEventFlows() {
    }

    static List<ProtocolFlows.Flow> all() {
        return List.of(bootstrap(), boot(), periodic(), valueChange(), connectionRequest(), scheduleInform());
    }

    /** 0 BOOTSTRAP：设备首次入网。 */
    private static ProtocolFlows.Flow bootstrap() {
        return new ProtocolFlows.Flow("0 BOOTSTRAP",
                "设备首次接入 ACS（或恢复出厂后重新登记）时上报的事件。"
                        + "ACS 收到 BOOTSTRAP 后通常完成设备建档，并下发初始配置（ConnectionRequestURL 凭据、周期参数、业务配置）。"
                        + "本模拟器启动时由 BootLauncher 分批触发：boot-batch-size 台/批、批间 boot-batch-delay 毫秒、"
                        + "批内再叠加 boot-jitter 随机抖动，避免万级设备瞬间冲击 ACS；"
                        + "inform-schedules.bootstrap（默认 86400s + 抖动 0.2）控制周期性重发，interval=0 可禁用。",
                "启动批量阶段：每台设备第一条 Inform，Event=\"0 BOOTSTRAP\"",
                "正常走会话总览流程，ACS 返回 204 结束；此后该设备转入 BOOT/PERIODIC 调度",
                lifelines(),
                eventMessages("0 BOOTSTRAP",
                        "Event=\"0 BOOTSTRAP\" + RetryCount=0 + 9 个固定参数（DeviceInfo、ManagementServer 摘要）"));
    }

    /** 1 BOOT：设备重启。 */
    private static ProtocolFlows.Flow boot() {
        return new ProtocolFlows.Flow("1 BOOT",
                "每次设备上电或重启后上报，告知 ACS「我重启过了」。"
                        + "本模拟器中触发点：Reboot RPC 完成（设备 OFFLINE → 等待 reboot-delay 秒 → ONLINE）后、"
                        + "FactoryReset RPC 完成后，各触发一次 1 BOOT Inform；"
                        + "inform-schedules.boot（默认 3600s + 抖动 0.2）控制周期重发。",
                "Reboot/FactoryReset 模拟完成、设备回到 ONLINE 后的第一条 Inform，Event=\"1 BOOT\"",
                "ACS 感知重启事件并刷新设备状态，会话 204 结束",
                lifelines(),
                eventMessages("1 BOOT", "Event=\"1 BOOT\"；reboot-delay（默认 5s）模拟真实重启耗时"));
    }

    /** 2 PERIODIC：周期心跳。 */
    private static ProtocolFlows.Flow periodic() {
        return new ProtocolFlows.Flow("2 PERIODIC",
                "周期心跳上报，维持 ACS 对设备在线状态与时序数据的感知，也是最常见的会话触发方式。"
                        + "本模拟器由 InformScheduler 按 inform-schedules.periodic（默认 300s + 初始抖动 0.3）"
                        + "scheduleAtFixedRate 调度；interval=0 禁用。"
                        + "手动触发路径也走此事件：CLI device inform / device inform --random、"
                        + "GUI 设备表右键「触发 Inform」、启动时键盘 'i'。"
                        + "xmpp-only 模式下，周期 Inform 在每台设备 XMPP ONLINE 后才开始（XmppOnlyInformBridge），"
                        + "XMPP 断开即暂停。",
                "到达调度时刻（初始延迟 = interval × (1 ± jitter) 随机打散，之后固定 interval）",
                "ACS 视其为心跳，可顺带下挂 RPC；会话 204 结束",
                lifelines(),
                eventMessages("2 PERIODIC", "Event=\"2 PERIODIC\"；全局限流 max-sessions（默认 1000）并发会话信号量"));
    }

    /** 4 VALUE CHANGE：主动通知参数值变化。 */
    private static ProtocolFlows.Flow valueChange() {
        return new ProtocolFlows.Flow("4 VALUE CHANGE",
                "notification=2（active）的参数值发生变化时，设备主动上报变更。"
                        + "本模拟器触发点：SetParameterValues 修改了 notification=2 参数且值确实变化；"
                        + "CLI/GUI 的 event value-change（数据模型 setValue + 触发本事件）。"
                        + "模型中带 notification=2 的参数示例：GPON 链路状态、XMPP.Username/Password。",
                "writable 且 notification=2 的参数被改写且新旧值不同 → 立即触发 Inform",
                "ACS 在 Inform 参数列表中读取变更后的值，会话 204 结束",
                lifelines(),
                eventMessages("4 VALUE CHANGE",
                        "Event=\"4 VALUE CHANGE\" + 变更后的参数值（Inform 携带当前快照）"));
    }

    /** 6 CONNECTION REQUEST：回连触发。 */
    private static ProtocolFlows.Flow connectionRequest() {
        return new ProtocolFlows.Flow("6 CONNECTION REQUEST",
                "ACS 主动发起回连（HTTP GET /connReq 或 XMPP connectionRequest IQ）后，"
                        + "设备以此事件建立 CWMP 会话，表示「本次会话由 ACS 回连触发」。"
                        + "详细回连路径见「TR-069 回连流程」页签。"
                        + "若设备已有活跃会话，回连请求直接应答（HTTP 200 / XMPP result），不再重复触发。",
                "CwmpServer 接受 HTTP 回连，或 ConnReqAuthHandler 验通 XMPP 回连凭据 → 触发 Inform",
                "ACS 借该会话下发预期指令（配置变更、重启、下载等），会话 204 结束",
                lifelines(),
                eventMessages("6 CONNECTION REQUEST", "Event=\"6 CONNECTION REQUEST\""));
    }

    /** M ScheduleInform：预约上报。 */
    private static ProtocolFlows.Flow scheduleInform() {
        return new ProtocolFlows.Flow("M ScheduleInform",
                "ACS 通过 ScheduleInform RPC 预约的一次性未来上报（\"M\" 前缀表示 ACS 侧方法触发）。"
                        + "本模拟器的 ScheduleInformHandler 在虚拟线程中按 DelaySeconds 延迟后触发本事件；"
                        + "CommandKey 原样带回，便于 ACS 关联预约上下文。",
                "ScheduleInform RPC 指定的 DelaySeconds 倒计时结束 → 触发 Inform",
                "ACS 在预约时刻收到数据（典型：定时采集、错峰上报），会话 204 结束",
                lifelines(),
                eventMessages("M ScheduleInform", "Event=\"M ScheduleInform\" + CommandKey=ACS 下发的 key"));
    }

    private static List<SequenceDiagram.Lifeline> lifelines() {
        return List.of(ProtocolFlows.CPE, ProtocolFlows.ACS);
    }

    private static List<SequenceDiagram.FlowMessage> eventMessages(String event, String informDetail) {
        List<SequenceDiagram.FlowMessage> messages = new ArrayList<>();
        messages.add(ProtocolFlows.msg(0, 1, "HTTP POST Inform（CPE → ACS）", informDetail,
                SequenceDiagram.Kind.START));
        messages.add(ProtocolFlows.msg(1, 0, "200 OK InformResponse（ACS → CPE）",
                "MaxEnvelopes > 0 时 CPE 继续空 POST 等待下挂 RPC",
                SequenceDiagram.Kind.MESSAGE));
        messages.add(SequenceDiagram.FlowMessage.section("可选：ACS 下挂 RPC 请求 ⇄ CPE 应答（见「TR-069 RPC 流程」）"));
        messages.add(ProtocolFlows.msg(1, 0, "204 No Content（ACS → CPE）",
                "双方无挂起消息 → 会话结束",
                SequenceDiagram.Kind.END));
        return messages;
    }
}
