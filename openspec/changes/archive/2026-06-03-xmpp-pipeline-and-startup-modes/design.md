## Context

当前 XMPP 子系统所有 Handler 类已实现但从未组装：
- `XmppStanzaDispatcher` 存在于代码中但未被添加到任何 Netty pipeline
- `StreamHandler`、`SaslHandler`、`BindHandler`、`PresenceHandler`、`IqHandler`、`PingHandler` 均未被实例化
- `XmppChannelInitializer` 仅添加了 Decoder 和 Encoder，pipeline 在编解码后终止
- `connectionRequestCallback` 在 `SimulationEngine` 中创建，传入 `XmppEngine`，但 `XmppEngine` 未将其传递给 `XmppConnection` 或 `IqHandler`
- `CpeDevice` 和 `XmppConnection` 各自维护独立的状态机，无状态同步机制
- `SimulationEngine.start()` 将 BOOT inform 和周期上报硬编码为必选步骤

## Goals / Non-Goals

**Goals:**
- 使 XMPP 连接能完成完整的握手流程（TCP → Stream → SASL → Bind → Presence → Online）
- 使 ACS 通过 XMPP IQ 发起的连接请求能正确触发 CWMP Inform 会话
- XmppConnection 上线后自动同步 CpeDevice 状态
- 提供 `--xmpp-only` 模式：仅 XMPP 连接，不启动 CwmpServer、不发 BOOT inform、不启动周期上报
- 提供 `--no-periodic` 选项：发 BOOT inform 但不启动周期上报

**Non-Goals:**
- 不实现 XMPP 的完整规范（仅实现 TR-069 所需的最小集）
- 不修改现有 public 模式的行为
- 不引入第三方 XMPP 库
- 不实现 KeepaliveHandler（PingHandler 已存在，keepalive 由现有机制处理）

## Decisions

### 决策 1：Pipeline 组装位置 — XmppConnection.connect() 中内联组装

**选择**：在 `XmppConnection.connect()` 方法中创建所有 Handler 并组装 pipeline，替代当前的 `XmppChannelInitializer`。

**理由**：
- Handler 需要引用 `XmppConnection` 实例（发送 stanza、更新状态），在 connect 时组装最自然
- `XmppChannelInitializer` 当前只接收 `Consumer<Channel>` 回调，无法传递 Handler 依赖
- 每个连接拥有独立的 Handler 实例，避免跨连接状态污染

**替代方案**：重构 `XmppChannelInitializer` 接收 Handler 工厂。增加了间接层但没有实质收益。

### 决策 2：connectionRequestCallback 传递方式 — 构造函数注入

**选择**：`XmppEngine` 在创建 `XmppConnection` 时通过构造函数传入 `connectionRequestCallback`。

**理由**：
- `XmppConnection` 已有 `deviceId` 和 `config` 两个构造参数，增加 callback 保持一致性
- 避免 setter 注入导致的状态不一致（callback 在连接生命周期内不变）

### 决策 3：状态桥接 — 选择性回调式

**选择**：`XmppConnection` 接受一个 `onStateChange` 回调（`Consumer<ConnectionState>`），在 `setState()` 中触发。`SimulationEngine` 注册此回调，但仅对三个终态做映射，中间态忽略。

**状态映射规则**：

| XmppConnection 状态 | CpeDevice 状态 | 是否映射 |
|---|---|---|
| ONLINE | ONLINE | 是 |
| FAILED | ERROR | 是 |
| DISCONNECTED | OFFLINE | 是 |
| CONNECTING | — | 否，保持当前状态 |
| AUTHENTICATING | — | 否 |
| AUTHENTICATED | — | 否 |
| BINDING | — | 否 |
| BOUND | — | 否 |

**理由**：
- 事件驱动，无轮询开销
- 解耦：`XmppConnection` 不需要知道 `CpeDevice` 的存在
- 回调在 `XmppConnection.setState()` 中触发，覆盖所有 10 个调用点
- 选择性映射避免中间态（如重连时 CONNECTING）错误覆盖 CpeDevice 状态

**线程安全分析**：

`setState()` 的调用方分布在不同线程中：
- `SaslHandler` / `BindHandler` / `PresenceHandler`：通过 `XmppStanzaDispatcher` 在虚拟线程中执行
- `XmppConnection.connect()` 失败回调：在 Netty NIO EventLoop 线程中
- `XmppEngine.healthCheck()`：在 ScheduledExecutorService 线程中
- `PingHandler` 超时：在 ScheduledExecutorService 线程中

回调中仅执行 `device.setState()`（volatile write），该操作在任何线程中均安全。如将来回调逻辑扩展为阻塞操作，需通过 `scheduler.execute()` 异步化。

**重连场景**：

`XmppEngine.healthCheck()` 检测到 FAILED/DISCONNECTED 时，先 `setState(DISCONNECTED)` 再 `connect()`。回调触发序列为：DISCONNECTED → CpeDevice.OFFLINE，随后重连成功后 ONLINE → CpeDevice.ONLINE。重连期间 CpeDevice 为 OFFLINE，`triggerInform()` 正确拒绝，行为符合预期。

**CpeDevice.connect() 保持不变**：
- public 模式：`connect()` 直接设 `DeviceState.ONLINE`（无 XMPP）
- NAT 模式：`connect()` 设 `DeviceState.CONNECTING`，等待 XMPP 回调设为 ONLINE

**替代方案**：
- 轮询式（`CpeDevice.connect()` 检查 `xmppConnection.isOnline()`）：阻塞，不适合大量设备
- 直接查询式（`triggerInform()` 中检查 `xmppConnection.isOnline()`）：增加 CpeDevice 与 XmppConnection 耦合，且 `status` 命令显示的设备状态不准确
- 事件总线：过度设计，仅有一个消费者
- XmppEngine 集中管理：多一层间接，当前场景不需要

### 决策 4：启动阶段拆分 — 布尔标志

**选择**：在 `AppConfig` 中新增 `xmppOnly`（boolean）和 `noPeriodic`（boolean）两个配置项，`SimulationEngine.start()` 根据标志决定执行哪些阶段。

**理由**：
- 与现有配置模式一致（`natMode` 也是 boolean）
- CLI 映射简单：`--xmpp-only` → `config.setXmppOnly(true)`
- `--xmpp-only` 隐含 `--no-periodic`（不启动周期上报），代码中显式处理

**替代方案**：
- 枚举式 `--start-phase xmpp|boot|full`：语义更清晰但与现有 boolean 配置风格不一致，且只有三种状态，枚举过度

### 决策 5：xmpp-only 模式下 CwmpServer 不启动

**选择**：`--xmpp-only` 模式下跳过 `CwmpServer.start()`。

**理由**：
- xmpp-only 的连接请求走 XMPP IQ，不需要 HTTP 监听
- 减少不必要的端口占用
- `--no-periodic` 模式下 CwmpServer 仍然启动（ACS 可能通过 HTTP 发连接请求）

## Risks / Trade-offs

- **[风险] Handler 组装顺序错误导致握手失败** → 通过集成测试验证完整 XMPP 握手流程；Handler 之间的依赖关系通过 setter 注入明确表达
- **[风险] 状态回调在 Netty IO 线程中执行** → 回调中仅做状态赋值（volatile write），不执行阻塞操作；如需复杂逻辑，在回调中通过 `scheduler.execute()` 异步处理
- **[风险] xmpp-only 模式下手动 device inform 可能因设备未上线而失败** → `device inform` 命令已有状态检查，返回明确错误信息；用户需等待 XMPP 握手完成
- **[权衡] XmppConnection 构造函数参数增加** → 可接受，参数数量在合理范围内（4个）
