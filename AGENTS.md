# AGENTS.md

## 构建与运行

- 构建：`mvn clean package`，产物为 `target/cpe-simulator.jar`（fat JAR）
- Windows 桌面安装包：`mvn clean package -Pwindows-installer`，产物为 `target/installer/CpeSimulator/`（app-image 免安装目录，内嵌裁剪 JRE）；安装 WiX Toolset 3.11+ 后可加 `-Djpackage.type=exe` 生成安装向导
- 运行测试：`mvn test`
- 运行单个测试类：`mvn test -Dtest=SoapBuilderTest`
- 运行单个测试方法：`mvn test -Dtest=SoapBuilderTest#testBuildInform`
- 编译和测试均需要 JDK 21，且启用了 `--enable-preview`（pom.xml 已配置）；class 文件带 preview 标志，**运行 JAR 与 jpackage 安装包时也必须带 `--enable-preview`**（jpackage profile 已内嵌该参数）
- 无 lint / formatter / typecheck 命令

## 架构要点

- 单模块 Maven 项目，入口类：`com.cpe.simulator.CpeSimulatorApp`（双模式分发：无参数/`--gui` → JavaFX 桌面应用；带 CLI 子命令 → picocli CLI）
- 包结构：
  - `cli` — picocli 命令（start/stop/status/device/event）+ UDS 服务端/客户端
  - `engine` — 设备生命周期编排（SimulationEngine、DeviceManager、InformScheduler、VendorProfile 厂商模型）
  - `cwmp` — TR-069 CWMP 协议（SOAP 构建/解析、会话状态机、RPC 处理器、Netty HTTP 服务器）
  - `xmpp` — 轻量 XMPP 客户端（NAT 模式，Netty 实现，非第三方 XMPP 库）
  - `model` — 数据模型层（YAML 加载、模板变量解析、参数树）
  - `common` — 配置加载、ID 生成、日志、XML 工具；`common/event` — 引擎事件总线（SimEvent/SimEventHub）
  - `desktop` — JavaFX 桌面层（完全独立，引擎不依赖它）：`bridge`（EngineController 引擎控制、UiEventBridge 事件节流、UiLogAppender 日志桥接）+ `ui`（MainWindow、各功能视图、SequenceDiagram 时序图组件）
- 运行时管理接口通过 Unix Domain Socket `/tmp/cpe-sim.sock`（JSON 协议），CLI 子命令（除 start）均通过 UDS 与运行中实例通信
- 内置数据模型 YAML 位于 `models/`（tr181.yaml、tr098.yaml），运行时从 JAR 同级目录解析；jpackage 安装包将其置于 `app/models/`。公共 DeviceInfo 参数共享于 `models/deviceinfo.yaml`（相对路径，按模型根前缀合并），模型独有的 DeviceInfo 参数留在各自主文件；自定义模型路径仍为单文件自包含
- 桌面资源：`src/main/resources/desktop/app.css`（主题，`:dark` 伪类切深浅色）、`src/main/resources/icons/`（窗口图标，由 `src/test/java/.../desktop/util/IconGenerator` 的 main 方法生成，同时产出 `src/jpackage/app.ico`）

## 引擎生命周期（两条路径，语义严格分离）

- **CLI 路径**：`SimulationEngine.start()` = 初始化 + 基础设施 + 创建 count 台设备 + 全部自动上线（connectBatch/launchBoot/周期调度）。此行为不可改变
- **GUI 路径**（新增公开 API，CLI 不使用）：
  - `initialize()` — 仅加载模型与设备管理器（不开监听），支持启动前新增设备
  - `startHeadless()` — 仅启动基础设施（回连监听/XMPP 引擎/调度器），忽略 count
  - `addDevices(vendor, count, overrides)` — 仅创建（CREATED），厂商字段（Manufacturer/OUI/ProductClass/回连凭据）从 VendorProfile 写入模型树
  - `startDevices(ids)` / `stopDevices(ids)` — 勾选设备上线/下线
  - `restoreDevice(vendor, serial, overrides)` — 按原序列号重建（引擎重启后恢复设备池，deviceId 不变）
- per-厂商回连认证：`CwmpServerHandler` 先解析 deviceId 再按厂商凭据认证（回退全局）；`ConnReqAuthConfig.credentialsFor(deviceId)` 同策略（XMPP 路径）；`CwmpServer.setCredentialProvider` 注入

## 注意事项

- 新增 RPC 方法需在 `RpcRegistry` 注册，并实现 `RpcHandler` 接口
- XMPP 实现为手写轻量协议栈（Stream/SASL/Bind/Presence/IQ/Ping），不支持完整 XMPP 规范
- SOAP 消息使用 StringBuilder 手动拼接 XML，非 JAXB 生成
- 并发模型依赖 Java 21 虚拟线程 + Semaphore 限流，修改并发逻辑时注意 `max-sessions` 配置
- 集成测试（`PublicModeIntegrationTest`、`NatModeIntegrationTest`）会启动真实网络端口，CI 环境需注意端口冲突
- 引擎事件钩子位于 `SimLogger` 三个结构化方法（connectionState/cwmpRpc/xmppEvent），事件发布在设备过滤器之前且不受其影响；`SimEventHub` 无监听者时零开销，新增 UI 消费点经 `UiEventBridge` 批量合并到 FX 线程
- 桌面层约定：引擎调用一律在虚拟线程执行（EngineController/各视图的 `runAsync`），禁止阻塞 FX 线程；视图轮询周期 1s（仪表盘/设备表），事件管线节流 250ms

## 使用中文输出所有结果
