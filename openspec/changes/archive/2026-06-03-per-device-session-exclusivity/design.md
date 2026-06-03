## Context

当前 CPE 模拟器在处理 ACS Connection Request 时存在并发安全问题。多个 Connection Request 可同时到达同一设备，每个都会通过 `triggerInform()` 异步提交一个 `runInformSession()` 任务到 scheduler。由于 `triggerInform()` 不检查设备是否已有活跃会话，多个 Inform 会话任务会并发执行。第二个任务的 `CwmpSession.startSession()` 会暴力调用 `close()` 关闭第一个正在进行的会话，导致第一个会话的 `processAcsRequests()` 循环因 `session.getState() != IN_SESSION` 提前退出，造成数据丢失。

现有的 `sessionSemaphore` 是全局的（限制所有设备并发会话总数 = `maxSessions`），不提供单设备互斥。`DeviceRegistry.handleConnectionRequest()` 有基于 `CwmpSession.state` 的检查，但存在竞态（检查与触发异步执行不在同一原子操作中），且 `SimulationEngine` 的 lambda handler 完全不检查会话状态。

CWMP 协议要求同一 CPE 与 ACS 之间同一时间只能有一个活跃会话。会话活跃时收到 Connection Request 是多余的（ACS 已能在当前会话中通信），应仅确认接收而不触发新 Inform。

## Goals / Non-Goals

**Goals:**
- 保证同一设备同一时间最多一个活跃或待启动的 Inform 会话（CWMP 单会话约束）
- 消除并发 Connection Request 导致的会话互相破坏的竞态条件
- 会话活跃时收到 Connection Request 返回确认（200 OK），不做额外动作
- 周期性 Inform 触发时若会话活跃则跳过，下次周期自动恢复

**Non-Goals:**
- 不实现排队等待机制（会话活跃时触发直接跳过，不排队延迟执行）
- 不改变全局 `sessionSemaphore` 的限流行为（`maxSessions` 语义不变）
- 不实现"在当前会话内通知 ACS 有新 Connection Request"的能力（模拟器是被动响应方，无法控制 ACS 行为）
- 不修改 `InformScheduler` 的调度策略（保持 `scheduleAtFixedRate`，不改为 `scheduleWithFixedDelay`）

## Decisions

### 决策 1：使用 AtomicBoolean CAS 作为设备级会话互斥锁

**选择**: 在 `CpeDevice` 中新增 `AtomicBoolean sessionActive`，通过 `compareAndSet(false, true)` 在 `triggerInform()` 入口处原子地检查并锁定。

**理由**:
- CAS 是无锁原子操作，零竞态风险
- 在异步提交到 scheduler **之前**就锁定，避免"检查时空闲但提交后冲突"的窗口
- 语义清晰：`sessionActive = true` 表示设备已承诺启动或正在运行一个会话
- 释放点明确：`runInformSession()` 的 finally 块中 `sessionActive.set(false)`

**替代方案**:
- Per-device Semaphore(1)：语义冗余（只需 on/off 状态），且 tryAcquire 等待行为不必要
- ReentrantLock：更重，且不需要重入语义
- 仅检查 CwmpSession.state：存在竞态（检查与异步提交不在同一原子操作）

### 决策 2：会话活跃时 Connection Request 的响应策略

**选择**: 返回 200 OK（"请求已收到"），不触发新 Inform，不做额外动作。

**理由**:
- CWMP 协议语义：ACS 在活跃会话中已能与 CPE 通信，Connection Request 多余
- 返回 200 OK 确认接收，ACS 不需要重试
- 不排队：排队会增加复杂度，且模拟器场景中 ACS 通常不会在会话内发 Connection Request
- 不返回 503：503 表示"设备忙无法处理"，但设备其实已经在处理 ACS 的通信请求

**替代方案**:
- 503 + ACS 重试：可能导致无限重试循环（如果 ACS 在会话期间反复重试）
- 200 + 排队等待：增加队列管理和延迟执行复杂度，且对模拟器测试场景收益不大

### 决策 3：移除 CwmpSession.startSession() 中的暴力关闭逻辑

**选择**: 移除 `startSession()` 中 `if (state == State.IN_SESSION) { close(); }` 的分支。

**理由**:
- `AtomicBoolean sessionActive` 已保证不会有并发 `startSession()` 调用
- 保留暴力关闭是防御性代码，但在 CAS 保护下永远不会触发
- 移除后语义更清晰：`startSession()` 只负责初始化新会话，不负责清理旧会话

### 决策 4：triggerInform() CAS 失败时的行为

**选择**: CAS 失败时直接返回，跳过本次触发，不排队、不报错、不打日志（或仅 debug 级别日志）。

**理由**:
- 会话活跃时跳过是正常行为，不是异常
- 周期性 Inform 下次周期自然恢复，不丢失
- Connection Request 语义上已由 200 OK 确认，跳过是预期行为

## Risks / Trade-offs

- **[sessionSemaphore 等待期间阻塞其他触发]** → `sessionSemaphore.tryAcquire(30s)` 等待期间 `sessionActive` 已为 true，该设备所有新触发被跳过。这是合理的——等待期间不应再触发新 Inform。若 semaphore 获取最终失败，finally 块释放 `sessionActive`，下次触发可正常进行。Inform 本身丢失（与当前行为一致）。
- **[周期 Inform 在会话活跃时被跳过可能导致长时间无 Inform]** → 如果 ACS 持续发起 Connection Request 导致设备频繁在会话中，periodic Inform 可能连续被跳过。但 `scheduleAtFixedRate` 保证每个周期都会尝试，一旦会话空闲就能恢复。对模拟器测试场景影响极小。
- **[AtomicBoolean 不覆盖 CwmpSession.state 的所有语义]** → `sessionActive` 是"意图锁"，`CwmpSession.state` 是"实际状态"。两者在正常流程中同步，但在异常情况（如 scheduler 任务被 cancel）可能不一致。需确保 finally 块始终释放 `sessionActive`。