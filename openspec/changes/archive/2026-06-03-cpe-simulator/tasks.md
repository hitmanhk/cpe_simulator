## 1. 项目骨架搭建

- [x] 1.1 创建 Maven pom.xml，配置 Java 21 编译、依赖管理（Netty 4.1、Jackson XML/YAML、picocli、SLF4J/Logback、Aalto XML、JUnit 5、Mockito）
- [x] 1.2 配置 maven-shade-plugin 打包 fat JAR
- [x] 1.3 创建包结构：cli/、engine/、cwmp/、xmpp/、model/、common/
- [x] 1.4 创建 CpeSimulatorApp.java 主入口（main 方法）
- [x] 1.5 创建 logback.xml 日志配置
- [x] 1.6 创建 application.yaml 默认配置文件模板

## 2. 通用基础设施

- [x] 2.1 实现 AppConfig 全局配置类（Jackson YAML 映射），包含所有配置项
- [x] 2.2 实现 ConfigLoader 配置加载器（支持 CLI 参数覆盖配置文件）
- [x] 2.3 实现 XmlUtil 工具类（XML 转义、命名空间常量定义）
- [x] 2.4 实现 IdGenerator（UUID 生成、设备序列号生成、CWMP ID 生成）
- [x] 2.5 实现 SimLogger 日志封装（大规模场景下控制日志量，支持按设备 ID 过滤）

## 3. 数据模型层

- [x] 3.1 实现 ParameterValue 类（参数值 + XSD 类型：string/int/unsignedInt/boolean/dateTime/base64）
- [x] 3.2 实现 ParameterNode 类（树形节点：名称、值、类型、可写标志、通知属性、子节点 Map、实例标记）
- [x] 3.3 实现 DataModel 根节点类（参数树根、按路径查找/设置/列举参数）
- [x] 3.4 实现 ModelLoader（从 YAML 文件加载数据模型定义，构建参数树）
- [x] 3.5 实现 ModelTemplate 模板变量解析引擎（${random.uuid}、${device.serial}、${acs.url}、${random.ip} 等）
- [x] 3.6 创建内置 TR-181 默认数据模型 YAML（DeviceInfo、ManagementServer、WANDevice 常用参数子集）
- [x] 3.7 创建内置 TR-098 默认数据模型 YAML（DeviceInfo、ManagementServer、WANDevice 常用参数子集）
- [x] 3.8 实现 DataModel 深拷贝方法（每个 CPE 实例独立副本）
- [x] 3.9 实现 FactoryReset 恢复默认值逻辑
- [x] 3.10 编写 ModelLoader 和 DataModel 单元测试

## 4. XMPP 协议层 — Netty 基础框架

- [x] 4.1 实现 XmppStanza 数据模型（IQ、Presence、Auth、StreamFeatures 等 stanza 类型）
- [x] 4.2 实现 XmppStreamEncoder（Netty ChannelOutboundHandler，将 Stanza 对象编码为 XML 字节流）
- [x] 4.3 实现 XmppStreamDecoder（Netty ChannelInboundHandler，基于 Aalto XML 异步 STAX 解析 XML Stream，正确分帧 stanza）
- [x] 4.4 实现 XmppConfig 配置类（host、port、domain、user-prefix、password、resource、tls、sasl 机制）
- [x] 4.5 实现 XmppConnection 类（封装 Netty Channel，管理单个 XMPP 连接状态：DISCONNECTED/CONNECTING/AUTHENTICATED/BOUND/ONLINE）
- [x] 4.6 配置 Netty Bootstrap 和 ChannelPipeline（SslHandler(可选) → XmppStreamDecoder → XmppStreamEncoder → XmppStanzaDispatcher → KeepaliveHandler）
- [x] 4.7 编写 XmppStreamDecoder 单元测试（完整 stanza、分片数据、CDATA、自关闭标签）

## 5. XMPP 协议层 — 协议实现

- [x] 5.1 实现 StreamHandler（Stream 协商：发送 stream:stream 开标签、处理 stream:features）
- [x] 5.2 实现 SaslMechanism 接口和 PlainMechanism（SASL PLAIN 认证）
- [x] 5.3 实现 ScramSha1Mechanism（SASL SCRAM-SHA-1 多轮认证）
- [x] 5.4 实现 SaslHandler（根据服务器 features 选择认证机制，执行认证流程）
- [x] 5.5 实现 BindHandler（Resource Binding：发送 bind IQ，处理绑定结果）
- [x] 5.6 实现 PresenceHandler（发送上线/离线 Presence）
- [x] 5.7 实现 PingHandler（XEP-0199：响应服务器 Ping、主动发送 Ping keepalive）
- [x] 5.8 实现 IqHandler（IQ stanza 分发：识别 Connection Request IQ、回复 result、通知 CPE 触发 Inform）
- [x] 5.9 实现 XmppStanzaDispatcher（根据 stanza 类型分发到对应 Handler，业务逻辑在虚拟线程中执行）
- [x] 5.10 实现 ReconnectPolicy（指数退避重连：初始 1s、最大 60s、随机抖动分散重连风暴）
- [x] 5.11 实现 XmppEngine（管理所有 XMPP 连接：批量建立、健康检查、全局重连控制）
- [x] 5.12 编写 SASL、Bind、Ping 相关 Handler 的单元测试

## 6. CWMP 协议层 — SOAP 消息处理

- [x] 6.1 定义 SOAP 命名空间常量（soap-env、soap-enc、cwmp-1-0、xsd、xsi）
- [x] 6.2 实现 SoapEnvelope 数据模型（Header 含 cwmp:ID、Body 含方法名和参数）
- [x] 6.3 实现 SoapBuilder（StringBuilder + 预编译 XML 片段，构建各类 SOAP 消息）
- [x] 6.4 实现 SoapBuilder.buildInform()（DeviceId、Event、ParameterList、MaxEnvelopes、CurrentTime、RetryCount）
- [x] 6.5 实现 SoapBuilder.buildTransferComplete()（CommandKey、FaultCode、StartTime、CompleteTime）
- [x] 6.6 实现 SoapBuilder.buildGetRPCMethodsResponse()（方法名列表）
- [x] 6.7 实现 SoapBuilder.buildEmptyRequest()（空 body，表示无更多 RPC）
- [x] 6.8 实现 SoapBuilder 各类 Response 构建方法（GetParameterValuesResponse、SetParameterValuesResponse、GetParameterNamesResponse、DownloadResponse、RebootResponse、FactoryResetResponse 等）
- [x] 6.9 实现 SoapParser（Jackson XML 解析 ACS 下发的 SOAP 消息）
- [x] 6.10 实现 SoapParser 提取方法名和 cwmp:ID
- [x] 6.11 实现 SoapParser 各类请求解析方法（parseGetParameterValues、parseSetParameterValues、parseGetParameterNames、parseDownload、parseReboot、parseFactoryReset 等）
- [x] 6.12 实现 SoapParser.parseInformResponse()（提取 holdRequests 标志）
- [x] 6.13 实现 SoapParser.parseFault()（提取 FaultCode 和 FaultString）
- [x] 6.14 编写 SoapBuilder 和 SoapParser 单元测试

## 7. CWMP 协议层 — 会话与 RPC

- [x] 7.1 实现 CwmpSession 状态机（IDLE → INFORM_SENT → CPE_HOLD → ACS_HOLD → IDLE，含超时处理）
- [x] 7.2 实现 RpcHandler 接口（methodName() + handle(CpeDevice, SoapMessage) → SoapMessage）
- [x] 7.3 实现 RpcRegistry（Map<String, RpcHandler> 注册表，启动时注册所有 Handler）
- [x] 7.4 实现 InformHandler（CPE→ACS：构建 Inform SOAP，处理 InformResponse）
- [x] 7.5 实现 GetParameterValuesHandler（从 DataModel 读取参数值，处理不存在参数返回 Fault 9005）
- [x] 7.6 实现 SetParameterValuesHandler（写入 DataModel，校验权限返回 Fault 9008，校验类型返回 Fault 9007）
- [x] 7.7 实现 GetParameterNamesHandler（按路径和 NextLevel 列举参数名）
- [x] 7.8 实现 GetParameterAttributesHandler / SetParameterAttributesHandler
- [x] 7.9 实现 AddObjectHandler / DeleteObjectHandler（多实例对象增删）
- [x] 7.10 实现 DownloadHandler（模拟固件升级：立即回复 → 延迟 → TransferComplete → 更新 SoftwareVersion）
- [x] 7.11 实现 RebootHandler（模拟重启：回复 → 关闭会话 → 延迟 → 重置参数 → BOOT Inform → 重连 XMPP）
- [x] 7.12 实现 FactoryResetHandler（模拟恢复出厂：回复 → 重载默认模型 → 延迟 → BOOT Inform）
- [x] 7.13 实现 ScheduleInformHandler / GetQueuedTransfersHandler / GetAllQueuedTransfersHandler
- [x] 7.14 实现 GetRPCMethodsHandler / GetOptionsHandler / SetVouchersHandler / UploadHandler
- [x] 7.15 实现 TransferComplete 主动发送逻辑（Download/Upload 完成后在 CPE_HOLD 阶段发送）
- [x] 7.16 编写各 RPC Handler 单元测试

## 8. CWMP 协议层 — HTTP 通信

- [x] 8.1 实现 CwmpClient（JDK HttpClient + 虚拟线程，CPE→ACS HTTP POST，支持 session cookie 维护）
- [x] 8.2 实现 HTTP Digest 认证（ACS 返回 401 时计算 Digest response 重发请求）
- [x] 8.3 实现 CwmpClient 会话内多次 POST 的连接复用和 cookie 传递
- [x] 8.4 实现 CwmpServer（Netty HTTP codec，ACS→CPE 反向回连 HTTP 服务器）
- [x] 8.5 实现 CwmpServer 路由：GET /connReq/{deviceId} → 触发对应 CPE Inform
- [x] 8.6 实现 CwmpServer 错误处理（404 设备不存在、503 设备离线、401 认证失败）
- [x] 8.7 实现 CwmpServer HTTP 认证（可选，配置 conn-req-user/password 时启用）
- [x] 8.8 编写 CwmpClient 和 CwmpServer 单元测试

## 9. Connection Request 与设备注册

- [x] 9.1 实现 DeviceRegistry（ConcurrentHashMap<deviceId, CpeDevice>，注册/注销/查找）
- [x] 9.2 实现 Connection Request URL 自动配置（初始化时写入 ManagementServer.ConnectionRequestURL）
- [x] 9.3 集成公网模式回连流程（HTTP GET → 触发 Inform → CPE 主动 POST 到 ACS）
- [x] 9.4 集成 NAT 模式回连流程（XMPP IQ → 触发 Inform → CPE 主动 POST 到 ACS）

## 10. 模拟引擎

- [x] 10.1 实现 CpeDevice 类（封装单个 CPE 实例：设备标识、DataModel、CwmpSession、XmppConnection 引用、状态）
- [x] 10.2 实现 DeviceManager（批量创建 CPE 实例，分配唯一序列号，初始化 DataModel）
- [x] 10.3 实现 InformScheduler（定时调度 Periodic Inform，随机抖动 ±jitter）
- [x] 10.4 实现 BatchConnector（批量连接速率控制：每批 N 个，间隔 M ms）
- [x] 10.5 实现全局并发会话控制（Semaphore 限制 max-sessions）
- [x] 10.6 实现 SimulationEngine（编排核心：加载模型 → 创建设备 → 启动 ConnReq 服务器 → 建立 XMPP → 首次 Inform → 周期运行）
- [x] 10.7 实现优雅关闭流程（停止调度 → 等待活跃会话 → 断开 XMPP → 关闭服务器 → 退出）
- [x] 10.8 实现运行时设备动态增减（通过管理接口添加/移除 CPE 实例）
- [x] 10.9 实现资源监控统计（在线数、会话数、Inform 统计、JVM 内存、线程数、Socket 数）
- [x] 10.10 编写 SimulationEngine 和 DeviceManager 单元测试

## 11. CLI 界面

- [x] 11.1 实现 MainCommand（picocli @Command 主命令，注册子命令）
- [x] 11.2 实现 StartCommand（所有 start 参数定义、参数校验、调用 SimulationEngine）
- [x] 11.3 实现 StopCommand（优雅/强制/超时停止）
- [x] 11.4 实现 StatusCommand（人类可读表格输出 + JSON 输出）
- [x] 11.5 实现 DeviceCommand 子命令组（list/get/add/remove/inform）
- [x] 11.6 实现 EventCommand 子命令组（reboot/factory-reset/value-change）
- [x] 11.7 实现 VersionCommand
- [x] 11.8 实现 Unix Domain Socket 管理接口（服务端：监听 /tmp/cpe-sim.sock，JSON 请求/响应）
- [x] 11.9 实现 Unix Domain Socket 管理接口（客户端：CLI 子命令通过 UDS 发送请求）
- [x] 11.10 实现 daemon 模式（fork 子进程、PID 文件、日志重定向）
- [x] 11.11 实现前台运行时快捷键交互（s=status, i=inform, r=reboot, q=quit, h=help）
- [x] 11.12 实现启动过程进度条显示（数据模型加载 → 服务器启动 → XMPP 连接 → Inform 发送）
- [x] 11.13 编写 CLI 命令参数校验单元测试

## 12. 集成测试与打包

- [x] 12.1 编写端到端集成测试：公网模式启动 → Inform → ACS 模拟回复 → 会话完成
- [x] 12.2 编写端到端集成测试：NAT 模式启动 → XMPP 连接 → Connection Request → Inform
- [x] 12.3 编写并发压力测试：1000 个 CPE 同时发送 Inform，验证 Semaphore 限流
- [x] 12.4 验证 fat JAR 打包正确，`java -jar cpe-simulator.jar version` 正常输出
- [x] 12.5 验证 50K 连接场景下的内存占用 ≤ 3GB（JVM -Xmx3g）
