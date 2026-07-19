package com.cpe.simulator.desktop.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * One second-level tab per supported CWMP RPC method (17 total), each
 * documenting purpose, request/response signature, fault behavior and the
 * simulator-specific behavior (mirroring the *Handler implementations).
 */
final class ProtocolRpcFlows {

    private ProtocolRpcFlows() {
    }

    static List<ProtocolFlows.Flow> all() {
        return groups().stream()
                .flatMap(g -> g.flows().stream())
                .toList();
    }

    /** RPC 按用途分组，供侧边栏导航使用。 */
    static List<ProtocolFlows.FlowGroup> groups() {
        return List.of(
                new ProtocolFlows.FlowGroup("参数读写", List.of(
                        getParameterValues(), setParameterValues(), getParameterNames(),
                        getParameterAttributes(), setParameterAttributes())),
                new ProtocolFlows.FlowGroup("对象管理", List.of(addObject(), deleteObject())),
                new ProtocolFlows.FlowGroup("文件传输", List.of(
                        download(), upload(), getQueuedTransfers(), getAllQueuedTransfers())),
                new ProtocolFlows.FlowGroup("设备运维", List.of(reboot(), factoryReset(), scheduleInform())),
                new ProtocolFlows.FlowGroup("能力协商", List.of(getRpcMethods(), getOptions(), setVouchers())));
    }

    // ---------------- 参数读写 ----------------

    private static ProtocolFlows.Flow getParameterValues() {
        return rpc("GetParameterValues",
                "作用：ACS 批量读取参数当前值。最常用的查询 RPC。",
                "请求 ParameterNames 数组；支持部分路径（如 Device.WiFi.）→ 递归展开全部叶子参数",
                "响应 ParameterValueStruct{Name, Value, Type} 列表；路径不存在回 Fault 9005",
                "模拟器：从共享数据模型 + 设备覆盖层读取（值为模板变量解析后的实例值，如序列号、随机 IP）");
    }

    private static ProtocolFlows.Flow setParameterValues() {
        return rpc("SetParameterValues",
                "作用：ACS 批量写入参数值。配置下发的主通道。",
                "请求 ParameterList{Name, Value, Type} + ParameterKey（ACS 事务键，响应原样带回）；"
                        + "三重校验：存在性 → 可写性 → 类型/取值",
                "响应 SetParameterValuesResponse（Status=0）；"
                        + "Fault 9005 参数不存在 / 9007 类型或取值非法 / 9008 参数只读",
                "模拟器：写入设备覆盖层；notification=2 且值变化 → 触发 4 VALUE CHANGE Inform；"
                        + "写 XMPP.Connection.1.Username/Password → NAT 模式懒建立 XMPP 连接（connectXmpp）");
    }

    private static ProtocolFlows.Flow getParameterNames() {
        return rpc("GetParameterNames",
                "作用：ACS 发现设备参数树结构（能力遍历）。",
                "请求 ParameterPath + NextLevel；NextLevel=true 仅返回直接子级（对象/表节点带尾点号），"
                        + "false 递归返回全部叶子",
                "响应 ParameterInfoStruct{Name, Writable} 列表；路径不存在回 Fault 9005",
                "模拟器：遍历参数树，多实例表返回当前存活实例（如 WANIPConnection.1.）");
    }

    private static ProtocolFlows.Flow getParameterAttributes() {
        return rpc("GetParameterAttributes",
                "作用：读取参数的 Notification 与 AccessList 属性。",
                "请求 ParameterNames 数组（同 GetParameterValues 寻址规则）",
                "响应 AttributeStruct{Name, Notification, AccessList} 列表",
                "模拟器：按模型定义返回（默认 Notification=0、AccessList=Subscriber）");
    }

    private static ProtocolFlows.Flow setParameterAttributes() {
        return rpc("SetParameterAttributes",
                "作用：修改参数 Notification（0=关闭 / 1=passive / 2=active）与 AccessList。",
                "请求 SetParameterAttributesStruct{Name, NotificationChange, Notification, "
                        + "AccessListChange, AccessList} 数组",
                "响应空 SetParameterAttributesResponse；路径不存在回 Fault 9005",
                "模拟器：更新节点属性；置为 2 后该参数值变化将触发 4 VALUE CHANGE Inform");
    }

    // ---------------- 对象管理 ----------------

    private static ProtocolFlows.Flow addObject() {
        return rpc("AddObject",
                "作用：在多实例表（如 WANConnectionDevice、WiFi SSID）中新增一行实例。",
                "请求 ObjectName（以 \".\" 结尾的表路径）+ ParameterKey",
                "响应 AddObjectResponse{InstanceNumber, Status=0}；路径不是多实例表回 Fault 9005",
                "模拟器：克隆模板实例 \"1\" 的参数集，分配最小可用实例号");
    }

    private static ProtocolFlows.Flow deleteObject() {
        return rpc("DeleteObject",
                "作用：删除多实例表中的指定实例。",
                "请求 ObjectName（含实例号，如 .2.）+ ParameterKey",
                "响应 DeleteObjectResponse{Status=0}；实例不存在回 Fault 9005",
                "模拟器：删除实例并回收实例号（TreeSet 最小优先复用）");
    }

    // ---------------- 文件传输 ----------------

    private static ProtocolFlows.Flow download() {
        return async("Download",
                "作用：ACS 指示设备从文件服务器下载文件，典型场景为固件升级。"
                        + "异步模型：RPC 立即应答，实际结果稍后通过 TransferComplete Inform 上报。",
                "请求 FileType（\"1 Firmware Upgrade Image\" 等）/ URL / Username / Password / "
                        + "FileSize / TargetFileName / DelaySeconds",
                "响应 DownloadResponse{Status=0（未开始或排队）, StartTime, CompleteTime}",
                "模拟器：虚拟线程延迟 download-delay（默认 10s）后按 download-success-rate（默认 0.99）随机成败；"
                        + "失败 → TransferComplete Fault=9010；固件下载成功 → SoftwareVersion 改为 UPGRADED-时间戳；"
                        + "结果入 pendingTransfers 供 GetQueuedTransfers 查询",
                "延迟 download-delay 后触发 TransferComplete Inform（成功 Fault=0 / 失败 Fault=9010）");
    }

    private static ProtocolFlows.Flow upload() {
        return async("Upload",
                "作用：ACS 指示设备向文件服务器上传文件（日志、配置备份、诊断包）。异步模型同 Download。",
                "请求 FileType / URL / Username / Password / DelaySeconds",
                "响应 UploadResponse{Status=0, StartTime, CompleteTime}",
                "模拟器：延迟后产生 TransferComplete 并入 pendingTransfers",
                "延迟后触发 TransferComplete Inform");
    }

    private static ProtocolFlows.Flow getQueuedTransfers() {
        return rpc("GetQueuedTransfers",
                "作用：ACS 查询设备尚未上报结果的传输任务（Download/Upload 队列）。",
                "请求无参数",
                "响应 QueuedTransferStruct{CommandKey, State} 列表；State：1=完成、2=进行中、3=排队、4=失败",
                "模拟器：返回 pendingTransfers（FaultCode=0 → State 1，否则 State 4）");
    }

    private static ProtocolFlows.Flow getAllQueuedTransfers() {
        return rpc("GetAllQueuedTransfers",
                "作用：GetQueuedTransfers 的扩展版，响应额外携带 FileType / FileSize 等传输元数据。",
                "请求无参数",
                "响应 AllQueuedTransferStruct{CommandKey, State, FileType, FileSize, …} 列表",
                "模拟器：同 GetQueuedTransfers，附带传输类型信息");
    }

    // ---------------- 设备运维 ----------------

    private static ProtocolFlows.Flow reboot() {
        return async("Reboot",
                "作用：远程重启设备。注意：RebootResponse 立即返回，重启在应答之后才发生。",
                "请求 CommandKey（ACS 事务键）",
                "响应空 RebootResponse",
                "模拟器：设备 OFFLINE → 等待 reboot-delay（默认 5s）→ ONLINE → 触发 1 BOOT Inform",
                "重启完成后触发 1 BOOT Inform（见事件子页签）");
    }

    private static ProtocolFlows.Flow factoryReset() {
        return async("FactoryReset",
                "作用：恢复出厂设置，清除全部配置。",
                "请求无参数",
                "响应空 FactoryResetResponse",
                "模拟器：数据模型恢复到出厂快照（saveDefaults 保存的默认值，含模板解析后的实例值）"
                        + "→ ONLINE → 触发 1 BOOT Inform",
                "复位完成后触发 1 BOOT Inform");
    }

    private static ProtocolFlows.Flow scheduleInform() {
        return async("ScheduleInform",
                "作用：预约一次未来的 Inform 上报（定时采集 / 错峰上报）。",
                "请求 DelaySeconds + CommandKey",
                "响应空 ScheduleInformResponse",
                "模拟器：虚拟线程倒计时 DelaySeconds 后触发 M ScheduleInform Inform（CommandKey 原样带回）",
                "倒计时结束触发 M ScheduleInform Inform（见事件子页签）");
    }

    // ---------------- 能力协商 ----------------

    private static ProtocolFlows.Flow getRpcMethods() {
        return rpc("GetRPCMethods",
                "作用：ACS 探测设备支持的 RPC 方法集合（能力协商）。",
                "请求无参数",
                "响应 MethodList：本模拟器支持的全部 17 个方法名",
                "模拟器：返回 RpcRegistry 注册表完整清单");
    }

    private static ProtocolFlows.Flow getOptions() {
        return rpc("GetOptions",
                "作用：查询设备可选/扩展参数能力（TR-069 Option 机制，现网较少使用）。",
                "请求 OptionName 数组",
                "响应 OptionList（本模拟器返回空列表，表示无扩展选项）",
                "模拟器：固定 ACK 空 OptionList");
    }

    private static ProtocolFlows.Flow setVouchers() {
        return rpc("SetVouchers",
                "作用：ACS 下发数字凭证（TR-069 Annex G 信任锚/凭证机制）。",
                "请求 VoucherList（base64 编码的 Voucher）",
                "响应空 SetVouchersResponse（成功接收）",
                "模拟器：直接 ACK，不解析凭证内容");
    }

    // ---------------- 构建辅助 ----------------

    /** 同步 RPC：请求 → 响应/Fault，一跳完成。 */
    private static ProtocolFlows.Flow rpc(String name, String purpose,
                                          String requestFeature, String responseFeature,
                                          String simulatorNote) {
        return new ProtocolFlows.Flow(name, purpose + "\n" + simulatorNote,
                "ACS 在 CWMP 会话内下挂本 RPC（承载于 HTTP 响应体，ACS → CPE）",
                "CPE 在下一个 HTTP POST 请求体中返回响应或 SOAP Fault（CPE → ACS）",
                List.of(ProtocolFlows.CPE, ProtocolFlows.ACS),
                rpcMessages(name + " 请求", requestFeature, responseFeature, null));
    }

    /** 异步 RPC：立即应答 + 后续 Inform 收尾。 */
    private static ProtocolFlows.Flow async(String name, String purpose,
                                            String requestFeature, String responseFeature,
                                            String simulatorNote, String followUp) {
        return new ProtocolFlows.Flow(name, purpose + "\n" + simulatorNote,
                "ACS 在 CWMP 会话内下挂本 RPC（承载于 HTTP 响应体，ACS → CPE）",
                "RPC 本身立即应答；" + followUp,
                List.of(ProtocolFlows.CPE, ProtocolFlows.ACS),
                rpcMessages(name + " 请求", requestFeature, responseFeature, followUp));
    }

    private static List<SequenceDiagram.FlowMessage> rpcMessages(String request, String requestFeature,
                                                                 String responseFeature, String followUp) {
        List<SequenceDiagram.FlowMessage> messages = new ArrayList<>();
        messages.add(ProtocolFlows.msg(1, 0, request + "（ACS → CPE）", requestFeature,
                SequenceDiagram.Kind.START));
        messages.add(ProtocolFlows.msg(0, 1, "响应 / SOAP Fault（CPE → ACS）", responseFeature,
                followUp == null ? SequenceDiagram.Kind.END : SequenceDiagram.Kind.MESSAGE));
        if (followUp != null) {
            messages.add(ProtocolFlows.msg(0, 1, "后续新会话：" + followUp,
                    "原会话 204 正常结束后，由模拟器异步触发新 CWMP 会话上报",
                    SequenceDiagram.Kind.END));
        }
        return messages;
    }
}
