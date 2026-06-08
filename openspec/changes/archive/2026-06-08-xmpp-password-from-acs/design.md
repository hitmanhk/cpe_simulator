## Context

当前 NAT 非 xmpp-only 场景下，CPE 模拟器的 XMPP 连接使用全局固定密码（`AppConfig.XmppConfig.password`），所有设备共享同一凭证。ACS 无法通过 TR-069 为单个设备指定 XMPP 密码。XMPP 连接在启动时批量创建，与 CWMP 同时建立，且 XmppConnection 与 DataModel 参数树完全隔离。

关键代码位置：
- `SaslHandler.java:60` — 密码来源为全局 `config.getPassword()`
- `SimulationEngine.java:115-133` — 批量创建所有 XmppConnection
- `SetParameterValuesHandler.java:57-78` — 写入 DataModel 后无 XMPP 触发机制
- `XmppEngine.java` — 无 `addConnection()` 方法，不支持单设备后加连接
- YAML 模型文件 — 无 `XMPP.Connection` 参数节点

## Goals / Non-Goals

**Goals:**
- ACS 通过 SetParameterValues 下发 `XMPP.Connection.1.Password` 后，设备动态建立 XMPP 连接
- XMPP 用户名仍为 `deviceId.toLowerCase()`（与设备序列号一致），仅密码可由 ACS 配置
- XMPP 连接意外断开后，通过已有的 healthCheck + ReconnectPolicy 渐进式重连
- xmpp-only 等旧模式不受影响（xmppPassword 为 null 时 fallback 全局密码）

**Non-Goals:**
- 不支持 XMPP Enable 开关参数——密码非空即连接
- 不支持密码变更后的断开重连——密码一次性下发，后续不再变更
- 不支持 `XMPP.Connection` 多实例——仅 Connection.1
- 不支持 XMPP ServerAddress/Port/Resource 通过 ACS 下发——仍从全局配置读取
- 不修改 xmpp-only 模式的启动行为

## Decisions

### 1. XMPP 密码注入方式：方案 C — XmppConnection 独立字段

**选择**: XmppConnection 新增 `xmppPassword` 字段 + getter/setter，CpeDevice 作为编排层从 DataModel 读取密码并注入。

**替代方案**:
- A: XmppConnection 持有 DataModel 引用 → 增加 xmpp→model 包依赖
- B: XmppConnection 持有 CpeDevice 引用 → 依赖更重，双向引用

**理由**: 方案 C 最轻量，XmppConnection 不引入对 model/cwmp 层的新依赖。密码通过 setter 注入，SaslHandler 只从 XmppConnection 读取，职责清晰。

### 2. XMPP 连接触发方式：DeviceContext 接口新增 connectXmpp()

**选择**: DeviceContext 接口新增 `connectXmpp()` 方法，SetParameterValuesHandler 检测到 XMPP 参数后调用。

**替代方案**:
- DataModel 变更监听器 → DataModel 无监听机制，需大量改造
- SimulationEngine 回调 → 需额外回调传递链，比接口方法更复杂

**理由**: DeviceContext 是 SetParameterValuesHandler 已有的上下文接口，加一个方法最直接。CpeDevice 实现该方法时持有 xmppEngine、DataModel、XmppConnection，三者协调在同一处。

### 3. xmppEngine 注入方式：CpeDevice 持有 xmppEngine 字段

**选择**: CpeDevice 新增 `xmppEngine` 字段，SimulationEngine 启动后通过 setter 注入。

**理由**: CpeDevice 已有 `xmppConnection` 字段（同类依赖），加 `xmppEngine` 自然。connectXmpp() 需要调用 `xmppEngine.createConnection()` 和 `xmppEngine.addConnection()`，必须持有引用。

### 4. 启动时不创建 XMPP 连接

**选择**: NAT 非 xmpp-only 模式下，SimulationEngine 只创建 XmppEngine（启动 scheduler + healthCheck），不创建任何 XmppConnection。

**理由**: 设备没有初始 XMPP 密码，不应连接。CWMP 正常工作（Boot Inform 可在 CONNECTING 状态发出），ACS 下发密码后才建立 XMPP。

### 5. XMPP 参数路径检测

**选择**: SetParameterValuesHandler 在写入循环后，遍历参数列表检查路径是否以 `XMPP.Connection` 开头且 Password 非空且 xmppConnection == null，触发 `device.connectXmpp()`。

**理由**: 精确检测 XMPP 参数变化，避免对所有 SetParameterValues 调用都触发 XMPP 逻辑。

## Risks / Trade-offs

- **[设备状态停留在 CONNECTING]** NAT 非 xmpp-only 模式下，设备直到 XMPP 连接成功才变 ONLINE → 可接受，CONNECTING 状态下 CWMP Inform 正常工作
- **[XMPP 连接失败后重连]** healthCheck 每 30 秒扫描，ReconnectPolicy 指数退避 1s→60s → 已有机制，无需额外处理。但首次失败后最长需等待 30 秒才被 healthCheck 发现
- **[xmpp-only 模式不受影响]** xmppPassword 为 null 时 SaslHandler fallback 全局密码 → 需确保 xmpp-only 模式启动时 xmppPassword 始终为 null
- **[密码只下发一次]** 不处理密码变更后的断开重连 → 简化设计，模拟器场景下密码通常只配置一次