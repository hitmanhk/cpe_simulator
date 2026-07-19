package com.cpe.simulator.desktop.ui;

import java.util.List;

/**
 * Protocol flow content for the ProtocolView, derived from the simulator's
 * actual implementation (CpeDevice session loop, XmppConnection handshake,
 * SaslHandler, XmppTlsSupport, CwmpServer/ConnReqAuthHandler).
 * Event flows live in {@link ProtocolEventFlows}, RPC flows in
 * {@link ProtocolRpcFlows}, CWMP fault codes in {@link ProtocolErrorCodes}.
 */
final class ProtocolFlows {

    record Flow(String tabTitle, String summary, String startMarker, String endMarker,
                List<SequenceDiagram.Lifeline> lifelines,
                List<SequenceDiagram.FlowMessage> messages) {
    }

    /** 侧边栏导航分组：组标题 + 该组下的流程列表。 */
    record FlowGroup(String title, List<Flow> flows) {
    }

    static final SequenceDiagram.Lifeline CPE =
            new SequenceDiagram.Lifeline("CPE（本模拟器）", "设备侧", "#3B82F6");
    static final SequenceDiagram.Lifeline ACS =
            new SequenceDiagram.Lifeline("ACS", "TR-069 服务器", "#1a7f37");
    static final SequenceDiagram.Lifeline XMPP =
            new SequenceDiagram.Lifeline("XMPP 服务器", "XMPP", "#8B5CF6");

    private ProtocolFlows() {
    }

    /** TR-069 CWMP 会话总览（事件细分见 ProtocolEventFlows）。 */
    static Flow cwmpSession() {
        return new Flow("会话总览",
                "CPE 与 ACS 之间的一次完整 CWMP 会话：Inform 上报 → 双向 RPC 交互 → 会话关闭。"
                        + "HTTP 双向承载：CPE 的消息放在 POST 请求体，ACS 的消息放在 HTTP 响应体。"
                        + "不同事件码的触发时机见后续事件子页签。",
                "CPE 发起 HTTP POST，请求体为 SOAP Inform（Event 携带事件码）",
                "ACS 返回 HTTP 204 No Content（空响应体），表示双方均无挂起消息，会话关闭",
                List.of(CPE, ACS),
                List.of(
                        msg(0, 1, "HTTP POST Inform（CPE → ACS）",
                                "SOAP Body = Inform：DeviceId(OUI+ProductClass+SerialNumber) + Event 事件码 + 参数列表",
                                SequenceDiagram.Kind.START),
                        msg(1, 0, "401 Unauthorized（ACS → CPE，可选）",
                                "仅当 ACS 要求认证；响应头 WWW-Authenticate 指明 Digest 或 Basic",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "重发 Inform（CPE → ACS，可选）",
                                "带 Authorization 头：Digest 摘要（MD5/SHA-256）或 Basic Base64 凭据",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "200 OK InformResponse（ACS → CPE）",
                                "MaxEnvelopes > 0 表示 ACS 将在本会话下挂 RPC 请求，CPE 需继续空 POST",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "HTTP 空 POST（CPE → ACS）",
                                "空请求体（仅 cwmp:ID 头）：CPE 无更多上行消息，等待 ACS 下发指令",
                                SequenceDiagram.Kind.MESSAGE),
                        SequenceDiagram.FlowMessage.section("RPC 交互循环（直到 ACS 没有更多请求，详见「TR-069 RPC 流程」页签）"),
                        msg(1, 0, "RPC 请求（ACS → CPE）",
                                "承载于 HTTP 响应体：GetParameterValues / SetParameterValues / AddObject / "
                                        + "DeleteObject / Download / Reboot / FactoryReset / ScheduleInform 等 17 种",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "RPC 响应（CPE → ACS）",
                                "承载于下一个 HTTP POST 请求体：XxxResponse 或 SOAP Fault（错误码见「TR-069 错误码」页签）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "204 No Content（ACS → CPE）",
                                "ACS 无更多请求且 CPE 无挂起消息 → 会话结束，CPE 关闭会话并释放会话信号量",
                                SequenceDiagram.Kind.END)));
    }

    /** TR-069 回连流程 · 公网模式（HTTP 回连）。 */
    static Flow connectionRequestHttp() {
        return new Flow("公网模式（HTTP 回连）",
                "公网模式下 ACS 可直接访问设备：向设备数据模型 ManagementServer.ConnectionRequestURL "
                        + "通告的地址发起 HTTP 回连。本模拟器由 CwmpServer（Netty）监听 "
                        + "conn-req-host:conn-req-port（默认 0.0.0.0:7547），路由 GET /connReq/{deviceId}。",
                "ACS 向设备回连 URL 发起 HTTP GET /connReq/{deviceId}",
                "设备触发 6 CONNECTION REQUEST Inform，主动与 ACS 建立 CWMP 会话（转入「TR-069 会话流程」）",
                List.of(CPE, ACS),
                List.of(
                        msg(1, 0, "HTTP GET /connReq/{deviceId}（ACS → CPE）",
                                "目标 = ConnectionRequestURL 通告的地址；仅接受 GET，其他路径/方法回 404",
                                SequenceDiagram.Kind.START),
                        msg(0, 1, "401 Unauthorized（CPE → ACS，可选）",
                                "仅当配置了回连认证（conn-req-user/password）；要求 HTTP Basic，"
                                        + "未通过则携带 WWW-Authenticate 质询",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "200 OK（CPE → ACS）",
                                "设备不存在回 404，离线回 503，已有活跃会话直接回 200；否则接受回连请求",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 0, "触发 6 CONNECTION REQUEST Inform",
                                "设备异步发起 CWMP 会话，ACS 的 200 响应不等待会话完成",
                                SequenceDiagram.Kind.END)));
    }

    /** TR-069 回连流程 · NAT 模式（XMPP 回连）。 */
    static Flow connectionRequestXmpp() {
        return new Flow("NAT 模式（XMPP 回连）",
                "NAT 模式下 ACS 无法直连 NAT 后的设备，按 TR-069 Amendment 6 经 XMPP 通道推送回连请求："
                        + "ACS 作为 XMPP 客户端向设备 JID 发送 connectionRequest IQ，"
                        + "本模拟器由 ConnReqAuthHandler 校验凭据后触发 CWMP 会话。",
                "ACS 经 XMPP 服务器向设备 JID 推送 <iq><connectionRequest/></iq>",
                "凭据校验通过 → 设备回 IQ result 并触发 6 CONNECTION REQUEST Inform（转入「TR-069 会话流程」）",
                List.of(CPE, XMPP, ACS),
                List.of(
                        msg(2, 1, "connectionRequest IQ（ACS → XMPP 服务器）",
                                "ACS 作为 XMPP 客户端向设备 JID 发送 IQ 集",
                                SequenceDiagram.Kind.START),
                        msg(1, 0, "<iq><connectionRequest/>（XMPP 服务器 → CPE）",
                                "命名空间 urn:broadband-forum-org:cwmp:xmppConnReq-1-0，可含 username/password 子元素",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<iq type=\"result\"/>（CPE → XMPP 服务器）",
                                "凭据与 conn-req-user/password 比对通过 → result；"
                                        + "失败 → <iq type=\"error\"><not-authorized/></iq>（附原 connectionRequest 体），不触发会话",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 0, "触发 6 CONNECTION REQUEST Inform",
                                "校验通过后异步触发：设备主动向 ACS 建立 CWMP 会话，完成 NAT 穿透",
                                SequenceDiagram.Kind.END)));
    }

    /** XMPP 建联流程。 */
    static Flow xmppHandshake() {
        return new Flow("XMPP 建联流程",
                "NAT 模式下设备与 XMPP 服务器建立连接并完成认证的完整握手："
                        + "TCP → XML 流 → （TLS）→ SASL → 资源绑定 → Presence 上线。",
                "TCP 连接建立（tls-mode=NONE/STARTTLS 默认 5222，DIRECT 默认 5223）",
                "CPE 发送 <presence/> 上线（状态 ONLINE）；此后进入 keepalive 阶段",
                List.of(CPE, XMPP),
                List.of(
                        msg(0, 1, "TCP 连接（CPE → XMPP 服务器）",
                                "DIRECT 模式随后立即 TLS 握手（见 TLS 流程 · DIRECT 子页签）",
                                SequenceDiagram.Kind.START),
                        msg(0, 1, "<stream:stream to=\"域\" version=\"1.0\">（CPE → 服务器）",
                                "打开 XML 流（STARTTLS/DIRECT 模式下此流在加密通道内）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "<stream:features>（服务器 → CPE）",
                                "宣告能力：SASL mechanisms、starttls、bind",
                                SequenceDiagram.Kind.MESSAGE),
                        SequenceDiagram.FlowMessage.section("可选：STARTTLS 升级（详见「TLS 流程 · STARTTLS」子页签）"),
                        SequenceDiagram.FlowMessage.section("SASL 认证（详见「SASL 流程」各子页签）"),
                        msg(0, 1, "重新 <stream:stream>（CPE → 服务器）",
                                "认证成功后流重启（stream restart）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "<stream:features>（服务器 → CPE）",
                                "含 <bind/> 特性，进入资源绑定阶段",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<iq type=\"set\"><bind><resource>sim</resource></bind></iq>（CPE → 服务器）",
                                "请求绑定资源名（默认 sim）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "<iq type=\"result\"><jid>…</jid></iq>（服务器 → CPE）",
                                "返回完整 JID（如 cpe0001@域/sim），设备记录 JID",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<presence/>（CPE → 服务器）",
                                "上线通告，状态迁移到 ONLINE，Inform 调度随之启动（xmpp-only 模式）",
                                SequenceDiagram.Kind.END),
                        msg(0, 1, "周期 <iq type=\"get\"><ping xmlns=\"urn:xmpp:ping\"/></iq>（保活）",
                                "每 keepalive-interval（默认 60s）一次；keepalive-timeout（默认 30s）内无 pong 判定断线，"
                                        + "指数退避重连（初始 reconnect-initial-delay，封顶 reconnect-max-delay）",
                                SequenceDiagram.Kind.MESSAGE)));
    }

    /** TLS 流程 · DIRECT。 */
    static Flow tlsDirect() {
        return new Flow("DIRECT（连接即 TLS）",
                "TCP 连接成功后不做任何明文协商，立即开始 TLS 握手，整个 XMPP 会话都在加密通道内。"
                        + "默认端口 5223。对应配置 xmpp.tls-mode=DIRECT（旧配置 tls: true 等效）。",
                "TCP 连接建立成功 → 立即发送 TLS ClientHello",
                "双方 Finished → 握手完成，CPE 在加密通道内发送 <stream:stream> 开始 XML 流",
                List.of(CPE, XMPP),
                List.of(
                        msg(0, 1, "TCP 连接（CPE → 服务器）",
                                "默认目标端口 5223", SequenceDiagram.Kind.START),
                        msg(0, 1, "ClientHello（CPE → 服务器）",
                                "TLS 版本、密码套件、SNI（服务器名）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "ServerHello + Certificate（服务器 → CPE）",
                                "服务器证书；模拟器信任所有证书（InsecureTrustManager），不校验证书链，"
                                        + "便于对接自签名证书的测试环境",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "KeyExchange + ChangeCipherSpec + Finished（CPE → 服务器）", "",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "ChangeCipherSpec + Finished（服务器 → CPE）",
                                "握手超时 10 秒；失败 → 状态 FAILED → 指数退避重连",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<stream:stream>（CPE → 服务器，加密）",
                                "握手完成后才开始 XML 流，后续 SASL/绑定/Presence 全部加密",
                                SequenceDiagram.Kind.END)));
    }

    /** TLS 流程 · STARTTLS。 */
    static Flow tlsStartTls() {
        return new Flow("STARTTLS（明文协商升级）",
                "先以明文打开 XML 流，通过 <starttls/> 协商后把现有 TCP 连接升级为加密通道，"
                        + "再重新开始 XML 流。默认端口 5222。对应配置 xmpp.tls-mode=STARTTLS。",
                "CPE 明文发送 <stream:stream>，服务器在 <stream:features> 中宣告 <starttls/> 能力",
                "升级成功后 CPE 重新发送 <stream:stream>（此后全部加密）；若服务器回 <failure/> 则连接 FAILED",
                List.of(CPE, XMPP),
                List.of(
                        msg(0, 1, "TCP 连接 + <stream:stream>（CPE → 服务器，明文）",
                                "默认目标端口 5222", SequenceDiagram.Kind.START),
                        msg(1, 0, "<stream:features> 含 <starttls/>（服务器 → CPE）",
                                "服务器宣告支持 TLS 升级",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<starttls/>（CPE → 服务器）",
                                "CPE 请求升级加密通道",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "<proceed/>（服务器 → CPE）",
                                "同意升级；若返回 <failure/> 则连接失败（状态 FAILED）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "TLS 握手（ClientHello … Finished）",
                                "在现有 TCP 连接上插入 SSL 层；握手超时 10 秒；模拟器信任所有证书",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "重新 <stream:stream>（CPE → 服务器，加密）",
                                "流重启，后续 SASL 认证在加密通道内进行",
                                SequenceDiagram.Kind.END)));
    }

    /** SASL 流程 · PLAIN。 */
    static Flow saslPlain() {
        return new Flow("PLAIN",
                "最简单的认证机制：用户名密码拼接后 Base64 一次性发送。凭据明文等价传输，"
                        + "强烈建议仅在 TLS 加密通道内使用。对应配置 xmpp.sasl-mechanism=PLAIN。",
                "CPE 发送 <auth mechanism=\"PLAIN\">（一条消息完成认证请求）",
                "服务器返回 <success/> → 认证通过，流重启；<failure/> → 状态 FAILED，指数退避重连",
                List.of(CPE, XMPP),
                List.of(
                        msg(0, 1, "<auth mechanism=\"PLAIN\">（CPE → 服务器）",
                                "内容 = Base64(\"\\0\" 用户名 \"\\0\" 密码)；用户名默认为设备序列号小写，"
                                        + "也可由 ACS 通过 XMPP.Connection.Username 下发",
                                SequenceDiagram.Kind.START),
                        msg(1, 0, "<success/>（服务器 → CPE）",
                                "认证通过 → CPE 重新打开 <stream:stream>（流重启），进入资源绑定",
                                SequenceDiagram.Kind.END),
                        msg(1, 0, "<failure/>（服务器 → CPE，异常路径）",
                                "凭据错误等 → 状态 FAILED → 指数退避重连",
                                SequenceDiagram.Kind.MESSAGE)));
    }

    /** SASL 流程 · SCRAM-SHA-1。 */
    static Flow scramSha1() {
        return scram("SCRAM-SHA-1（默认）", "SCRAM-SHA-1",
                "HMAC-SHA-1 作为伪随机函数的挑战-应答认证：密码本身永不上链，"
                        + "双方通过随机数 + 盐 + PBKDF2 迭代派生密钥并互相证明持有密码。"
                        + "本模拟器的默认机制（xmpp.sasl-mechanism=SCRAM-SHA-1）。");
    }

    /** SASL 流程 · SCRAM-SHA-256。 */
    static Flow scramSha256() {
        return scram("SCRAM-SHA-256", "SCRAM-SHA-256",
                "与 SCRAM-SHA-1 流程完全同构，区别仅在哈希算法升级为 HMAC-SHA-256 / SHA-256"
                        + "（Hi() PBKDF2 迭代、ClientKey/ServerKey 派生、Proof 与 ServerSignature 计算均用 SHA-256）。"
                        + "对应配置 xmpp.sasl-mechanism=SCRAM-SHA-256。");
    }

    private static Flow scram(String title, String mechanism, String intro) {
        return new Flow(title, intro,
                "CPE 发送 <auth mechanism=\"" + mechanism + "\">，首条消息即 client-first（含客户端随机数）",
                "服务器返回 <success v=…> 且 CPE 校验服务器签名（v=）一致 → 认证通过，流重启；"
                        + "签名不一致或 <failure/> → 状态 FAILED，指数退避重连",
                List.of(CPE, XMPP),
                List.of(
                        msg(0, 1, "<auth mechanism=\"" + mechanism + "\">（CPE → 服务器）",
                                "client-first：n,,n=用户名,r=客户端随机数（base64，16 字节随机生成）",
                                SequenceDiagram.Kind.START),
                        msg(1, 0, "<challenge>（服务器 → CPE）",
                                "r=客户端+服务器拼接随机数, s=盐(base64), i=PBKDF2 迭代次数；"
                                        + "CPE 校验 r 必须以客户端随机数开头（防钓鱼）",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(0, 1, "<response>（CPE → 服务器）",
                                "client-final：c=biws（channel binding）, r=完整随机数, p=客户端证明；"
                                        + "SaltedPassword=Hi(密码,盐,迭代) → ClientKey/StoredKey → Proof=ClientKey XOR HMAC(StoredKey, AuthMessage)",
                                SequenceDiagram.Kind.MESSAGE),
                        msg(1, 0, "<success v=…>（服务器 → CPE）",
                                "v=ServerSignature=HMAC(ServerKey, AuthMessage)；CPE 本地重算并比对，"
                                        + "一致才判定认证通过（双向验证，服务器也证明持有密码派生材料）",
                                SequenceDiagram.Kind.END),
                        msg(1, 0, "<failure/>（服务器 → CPE，异常路径）",
                                "任一环节失败：状态 FAILED → 指数退避重连（初始 reconnect-initial-delay 秒，"
                                        + "封顶 reconnect-max-delay 秒，±10% 抖动）",
                                SequenceDiagram.Kind.MESSAGE)));
    }

    static SequenceDiagram.FlowMessage msg(int from, int to, String label, String note,
                                           SequenceDiagram.Kind kind) {
        return new SequenceDiagram.FlowMessage(from, to, label, note, kind);
    }
}
