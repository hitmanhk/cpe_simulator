# ACS 集成测试系统（acs-it）

独立的 ACS/XMPP 测试替身 + 集成测试套件，通过**真实网络 socket** 驱动真实的
CPE 模拟器引擎，对全部协议能力做黑盒端到端验证。

与主项目 `src/test` 内的单元/集成测试不同：本系统从**协议对端**（ACS 服务器与
XMPP 服务器）视角发起并校验每一次交互，不复用模拟器自身的任何协议代码。

## 两种使用模式

### 模式一：自动化集成测试（JUnit 套件）

```bash
# 1. 先安装模拟器到本地仓库（acs-it 以它为依赖）
mvn install -DskipTests

# 2. 运行全部 47 个集成测试（约 5 分钟，127.0.0.1 随机端口，无需特权）
mvn -f acs-it/pom.xml test

# 3. 运行单个套件 / 单个方法
mvn -f acs-it/pom.xml test -Dtest=RpcMethodsIT
mvn -f acs-it/pom.xml test -Dtest=XmppFlowsIT#directTlsWithScramSha256
```

### 模式二：standalone 对端服务（实时消息测试）

把 ACS + XMPP 服务器作为**常驻进程**启动，模拟器（GUI 或 CLI）连上来即可
实时观察消息流、手工下发 RPC：

```bash
# 构建一次
mvn -f acs-it/pom.xml package -DskipTests

# 启动对端（默认 ACS=7547、XMPP=5222；可用参数改端口）
java --enable-preview -jar acs-it/target/acs-it-runner.jar \
  --acs-port 17547 --xmpp-port 15222 --domain iot.example.com
```

然后启动模拟器指向它（注意：直接运行 `target/cpe-simulator.jar` 时数据模型
按 JAR 同级 `models/` 解析，需加 `--model models/tr181.yaml`；dist zip /
安装包 / IDEA 运行则无需此参数）：

```bash
# public 模式（注意 CPE 回连端口不能与 ACS 端口相同）
java --enable-preview -jar target/cpe-simulator.jar start \
  --mode public --count 3 --acs-url http://127.0.0.1:17547/ \
  --conn-req-port 17548 --periodic-inform-interval 10 --model models/tr181.yaml

# nat 模式（XMPP 穿透）
java --enable-preview -jar target/cpe-simulator.jar start \
  --mode nat --xmpp-only --count 2 --acs-url http://127.0.0.1:17547/ \
  --conn-req-port 17548 --xmpp-host 127.0.0.1 --xmpp-port 15222 \
  --xmpp-domain iot.example.com --xmpp-password secret --model models/tr181.yaml
```

GUI 模式则在「配置」页填写同样的地址后点「启动模拟器」，消息可在
GUI 的「消息监视」页与 runner 控制台两侧同时观察。

启动后 runner 控制台实时打印每条消息：

```
[ACS] Inform   serial=SIM00001 events=[0 BOOTSTRAP] params=9
[XMPP] ONLINE  #1 jid=sim00000@iot.example.com/sim mechanism=SCRAM-SHA-1
```

交互命令（输入 `help` 查看）：

| 命令 | 作用 |
|---|---|
| `gpv <路径...>` | GetParameterValues（下次会话下发并打印响应） |
| `spv <名称>=<值>` | SetParameterValues |
| `gpn <路径> [true]` | GetParameterNames（可带 NextLevel） |
| `methods` / `reboot` / `factoryreset` / `download <url>` | 对应 RPC 下发 |
| `connreq [序号]` | 通过 XMPP 向在线设备发 TR-069 回连（触发 6 CONNECTION REQUEST） |
| `sessions` / `informs` | 查看 XMPP 会话 / 最近 Inform |
| `auth none\|basic\|digest-md5\|digest-sha256` | 切换 ACS 认证（admin/admin） |

## 架构

```
acs-it/
├── pom.xml                                  独立 Maven 项目（不改动主项目构建）
└── src/
    ├── main/java/com/cpe/acsit/
    │   ├── acs/                             ACS 测试替身
    │   │   ├── AcsServer.java               Netty HTTP 服务器：Inform 应答、
    │   │   │                                脚本化 RPC 队列下发、Basic/Digest 认证、Cookie
    │   │   ├── CwmpRpc.java                 全部 17 种 RPC 请求 + InformResponse 构建
    │   │   ├── CwmpMessage.java             CPE→ACS 消息解析（Inform/RPC 响应/Fault）
    │   │   ├── DigestVerifier.java          RFC 7616 服务端 Digest 校验（MD5/SHA-256）
    │   │   └── XmlNode.java                 轻量 XML 树解析
    │   ├── xmpp/                            XMPP 测试替身
    │   │   ├── XmppTestServer.java          Stream/STARTTLS/DIRECT TLS/SASL/Bind/
    │   │   │                                Presence/IQ Ping/TR-069 回连下发
    │   │   ├── ScramServer.java             RFC 5802 服务端 SCRAM 完整证明校验
    │   │   ├── StanzaTokenizer.java         增量 stanza 分割器
    │   │   ├── ClientSession.java           每连接会话记录（stanza 流/状态/ping 计数）
    │   │   └── TlsCertUtil.java             BouncyCastle 自签名证书（JDK 21 兼容）
    │   ├── harness/
    │   │   └── SimHarness.java              引擎启动基座（随机端口/模型路径/条件等待）
    │   └── standalone/
    │       └── StandaloneRunner.java        常驻对端服务 + 交互控制台（模式二入口）
    └── test/java/com/cpe/acsit/             9 个测试套件（47 个测试）
```

要求 JDK 21（surefire 已配置 `--enable-preview`，模拟器 class 文件带 preview 标志）。
测试全部使用 127.0.0.1 随机端口，无需 root/管理员权限；串行执行，总耗时约 5 分钟。

## 功能覆盖矩阵

| 功能域 | 覆盖点 | 测试位置 |
|---|---|---|
| **TR-069 会话** | BOOTSTRAP Inform 内容（DeviceId/事件码/9 参数） | `InformFlowIT` |
| | Inform → InformResponse → empty POST → RPC 串联 → 204 结束 | 全部 CWMP 套件隐式覆盖 |
| | 周期 Inform（interval=2s 重复到达） | `BehaviorIT.periodicInformRepeats...` |
| | ScheduleInform → `M ScheduleInform` | `BehaviorIT.scheduleInformFires...` |
| | 回连触发 `6 CONNECTION REQUEST` | `ConnectionRequestIT` / `BehaviorIT` |
| **17 种 RPC** | GetRPCMethods 返回全部 17 方法 | `RpcMethodsIT.getRpcMethods...` |
| | GetParameterValues（叶子/部分路径递归） | `RpcMethodsIT` |
| | SetParameterValues（写入+读回） | `RpcMethodsIT` |
| | GetParameterNames（NextLevel=true/false） | `RpcMethodsIT` |
| | GetParameterAttributes（默认值）/ SetParameterAttributes（notification 修改持久化） | `RpcMethodsIT` |
| | AddObject（实例号分配）/ DeleteObject（实例号回收复用） | `RpcMethodsIT` |
| | Download（Status=0 即应答）/ Upload / GetQueuedTransfers / GetAllQueuedTransfers | `RpcMethodsIT` |
| | GetOptions / SetVouchers | `RpcMethodsIT` |
| | Reboot → `1 BOOT` | `BehaviorIT` |
| | FactoryReset → `1 BOOT` + 默认值恢复 | `BehaviorIT` |
| | Download 成功（state=1 + SoftwareVersion 升级）/ 失败（state=4） | `BehaviorIT` |
| **Fault 路径** | 9000 未知方法 / 9005 无效参数名（GPV/SPV/DeleteObject） | `RpcFaultIT` |
| | 9007 类型不匹配 / 9008 只读参数写入 | `RpcFaultIT` |
| **TR-181 模型** | 模板变量解析（serial/oui/acs.url/conn.req.url/random.ip） | `DataModelIT` |
| | 模型专属路径（WiFi.SSID.1.SSID） | `DataModelIT` |
| **TR-098 模型** | InternetGatewayDevice. 根、WLAN 路径、TR-181 路径 9005 | `DataModelIT` |
| **参数通知** | notification=2 修改触发 `4 VALUE CHANGE` | `DataModelIT` |
| **HTTP 认证** | Basic 通过 / 错误密码拒绝 | `AcsAuthIT` |
| | Digest MD5 / SHA-256 通过（服务端完整校验）/ 错误密码拒绝 | `AcsAuthIT` |
| | Cookie：Set-Cookie 后会话内回传 | `AcsAuthIT` |
| **HTTP 回连** | 200 触发 / 未知设备 404 / Basic 认证 401→200 | `ConnectionRequestIT` |
| **XMPP 握手** | TLS NONE × PLAIN / SCRAM-SHA-1 / SCRAM-SHA-256 | `XmppHandshakeIT` |
| | DIRECT TLS × PLAIN / SCRAM-SHA-256 | `XmppFlowsIT` |
| | STARTTLS × PLAIN / SCRAM-SHA-1 | `XmppFlowsIT` |
| | 资源绑定（resource=sim，JID 格式） | 全部 XMPP 套件 |
| | XEP-0199 ping 保活（服务端应答+计数） | `XmppHandshakeIT` |
| | SASL 错误密码：服务端拒绝、客户端不到 ONLINE | `XmppFlowsIT` |
| **SCRAM 安全** | 服务端完整校验 ClientProof（Hi/PBKDF2 + HMAC + StoredKey） | `ScramServer`（所有 SCRAM 用例） |
| | 服务端签发 ServerSignature 供客户端校验 | `ScramServer` |
| **XMPP 回连** | connectionRequest IQ → result → CWMP `6 CONNECTION REQUEST` | `XmppFlowsIT` |
| **NAT 懒连接** | SetParameterValues 下发 XMPP 凭据（TR-181 路径）→ 懒建连上线 | `XmppFlowsIT` |
| | 同上（TR-098 路径） | `XmppFlowsIT` |
| **断线重连** | kill 连接 → 指数退避 → 新会话上线 | `XmppFlowsIT` |

## 关键设计

- **脚本化 ACS**：测试在引擎启动前将 RPC 请求入队，ACS 在会话内按 TR-069
  规则逐个下发（Inform 后 empty POST 触发），响应到达时完成对应 Future。
  这精确复刻真实 ACS 的会话内流水线行为，同时保证测试无时序竞态。
- **独立协议实现**：测试替身的 SOAP 解析、Digest 校验、SCRAM 证明校验、
  stanza 处理全部独立实现——模拟器协议栈的任何偏差都会暴露为测试失败，
  而不是被共享代码掩盖。
- **真实引擎进程内驱动**：`SimHarness` 以公开 API 启动 `SimulationEngine`
  （与生产 CLI/GUI 同一代码路径），绑定随机 loopback 端口，测试互不干扰。
