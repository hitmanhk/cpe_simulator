# AGENTS.md

## 构建与运行

- 构建：`mvn clean package`，产物为 `target/cpe-simulator.jar`（fat JAR）
- 运行测试：`mvn test`
- 运行单个测试类：`mvn test -Dtest=SoapBuilderTest`
- 运行单个测试方法：`mvn test -Dtest=SoapBuilderTest#testBuildInform`
- 编译和测试均需要 JDK 21，且启用了 `--enable-preview`（pom.xml 已配置）
- 无 lint / formatter / typecheck 命令

## 架构要点

- 单模块 Maven 项目，入口类：`com.cpe.simulator.CpeSimulatorApp`（picocli CLI）
- 包结构：
  - `cli` — picocli 命令（start/stop/status/device/event）+ UDS 服务端/客户端
  - `engine` — 设备生命周期编排（SimulationEngine、DeviceManager、InformScheduler）
  - `cwmp` — TR-069 CWMP 协议（SOAP 构建/解析、会话状态机、RPC 处理器、Netty HTTP 服务器）
  - `xmpp` — 轻量 XMPP 客户端（NAT 模式，Netty 实现，非第三方 XMPP 库）
  - `model` — 数据模型层（YAML 加载、模板变量解析、参数树）
  - `common` — 配置加载、ID 生成、日志、XML 工具
- 运行时管理接口通过 Unix Domain Socket `/tmp/cpe-sim.sock`（JSON 协议），CLI 子命令（除 start）均通过 UDS 与运行中实例通信
- 内置数据模型 YAML 位于 `src/main/resources/models/`（tr181-default.yaml、tr098-default.yaml）

## 注意事项

- 新增 RPC 方法需在 `RpcRegistry` 注册，并实现 `RpcHandler` 接口
- XMPP 实现为手写轻量协议栈（Stream/SASL/Bind/Presence/IQ/Ping），不支持完整 XMPP 规范
- SOAP 消息使用 StringBuilder 手动拼接 XML，非 JAXB 生成
- 并发模型依赖 Java 21 虚拟线程 + Semaphore 限流，修改并发逻辑时注意 `max-sessions` 配置
- 集成测试（`PublicModeIntegrationTest`、`NatModeIntegrationTest`）会启动真实网络端口，CI 环境需注意端口冲突
