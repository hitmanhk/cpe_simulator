## Why

当 ACS 对同一设备同时发送多个 Connection Request 时，当前代码存在竞态条件：多个 `triggerInform()` 异步提交后并发执行 `runInformSession()`，第二个会话的 `startSession()` 会暴力关闭正在进行的第一个会话，导致数据丢失和协议违规。CWMP 协议规定同一 CPE 与 ACS 之间同一时间只能存在一个会话；会话活跃时收到多余的 Connection Request 是无意义的（ACS 已能在当前会话中通信），应仅返回确认而不触发新 Inform。

## What Changes

- 在 `CpeDevice` 中新增 `AtomicBoolean sessionActive`，在 `triggerInform()` 中通过 CAS 保证同一设备只有一个 Inform 会话（活跃或待启动），拒绝并发触发
- `triggerInform()` CAS 失败时直接跳过，不排队、不报错——当前会话已覆盖该触发的通信目的
- HTTP/XMPP Connection Request handler 检查 `sessionActive`，会话活跃时返回 200 OK（不做额外动作），空闲时正常触发 Inform
- 移除 `CwmpSession.startSession()` 中暴力 `close()` 已有会话的逻辑（CAS 已保证不会并发 start）
- 周期性 Inform（`InformScheduler`）触发时若 `sessionActive` 为 true 则跳过，下次周期自动恢复

## Capabilities

### New Capabilities
- `session-exclusivity`: 单设备 CWMP 会话互斥机制，保证同一设备同一时间最多一个活跃或待启动的 Inform 会话

### Modified Capabilities

## Impact

- `CpeDevice.java` — 核心改动：新增 `sessionActive` 字段，修改 `triggerInform()` 和 `runInformSession()`
- `CwmpSession.java` — 移除 `startSession()` 中暴力关闭逻辑
- `SimulationEngine.java` — ConnectionRequest handler lambda 添加 `sessionActive` 检查
- `ConnectionRequestIntegration.java` — XMPP handler 添加 `sessionActive` 检查
- `DeviceRegistry.java` — `handleConnectionRequest()` 改为检查 `sessionActive`（替代 `CwmpSession.state`）
- 现有测试需适配新行为（会话活跃时 `triggerInform()` 被跳过）