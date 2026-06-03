## ADDED Requirements

### Requirement: 设备级会话互斥
CpeDevice SHALL 通过 `AtomicBoolean sessionActive` 保证同一设备同一时间最多一个活跃或待启动的 Inform 会话。`triggerInform()` 入口 SHALL 使用 `compareAndSet(false, true)` 原子操作：成功则提交 Inform 任务到 scheduler，失败则跳过本次触发。

#### Scenario: CAS 成功触发 Inform
- **WHEN** 设备 `sessionActive` 为 false 且 `triggerInform("6 CONNECTION REQUEST")` 被调用
- **THEN** `compareAndSet(false, true)` 成功，Inform 任务被提交到 scheduler，设备开始 Inform 会话

#### Scenario: CAS 失败跳过触发
- **WHEN** 设备 `sessionActive` 为 true（会话活跃或待启动）且 `triggerInform("2 PERIODIC")` 被调用
- **THEN** `compareAndSet(false, true)` 失败，Inform 任务不被提交，本次触发被跳过

#### Scenario: 会话结束后释放互斥锁
- **WHEN** Inform 会话完成（`runInformSession()` 的 finally 块执行）
- **THEN** `sessionActive` 被设置为 false，设备可接受下一次 Inform 触发

#### Scenario: semaphore 获取失败后释放互斥锁
- **WHEN** `runInformSession()` 中 `sessionSemaphore.tryAcquire()` 在 30 秒后超时失败
- **THEN** `sessionActive` 被设置为 false（在 finally 块中），设备可接受下一次 Inform 触发

### Requirement: 会话活跃时 Connection Request 不触发新 Inform
HTTP 和 XMPP Connection Request handler SHALL 检查设备的 `sessionActive` 状态。若设备会话活跃，SHALL 返回成功确认（HTTP 200 OK / XMPP IQ success）但不触发新 Inform；若设备空闲，SHALL 正常触发 Inform。

#### Scenario: HTTP Connection Request 设备空闲
- **WHEN** ACS 发送 HTTP GET `/connReq/<deviceId>` 且设备 `sessionActive` 为 false
- **THEN** handler 返回 `ConnectionRequestResult(true, 200, "OK")` 并调用 `triggerInform("6 CONNECTION REQUEST")`

#### Scenario: HTTP Connection Request 设备会话活跃
- **WHEN** ACS 发送 HTTP GET `/connReq/<deviceId>` 且设备 `sessionActive` 为 true
- **THEN** handler 返回 `ConnectionRequestResult(true, 200, "OK")` 但不调用 `triggerInform()`，不做额外动作

#### Scenario: HTTP Connection Request 设备离线
- **WHEN** ACS 发送 HTTP GET `/connReq/<deviceId>` 且设备状态为 OFFLINE
- **THEN** handler 返回 `ConnectionRequestResult(false, 503, "Device offline")`

#### Scenario: XMPP Connection Request 设备空闲
- **WHEN** XMPP 通道收到 Connection Request 且设备 `sessionActive` 为 false
- **THEN** 调用 `triggerInform("6 CONNECTION REQUEST")`

#### Scenario: XMPP Connection Request 设备会话活跃
- **WHEN** XMPP 通道收到 Connection Request 且设备 `sessionActive` 为 true
- **THEN** 不调用 `triggerInform()`，不做额外动作

### Requirement: 周期性 Inform 会话活跃时跳过
InformScheduler 的周期 Inform 触发 SHALL 通过 `triggerInform()` 的 CAS 机制保证：若设备会话活跃，周期 Inform 被跳过，下一个周期自动恢复。

#### Scenario: 周期 Inform 设备空闲
- **WHEN** InformScheduler 定时触发 `triggerInform("2 PERIODIC")` 且设备 `sessionActive` 为 false
- **THEN** CAS 成功，周期 Inform 正常执行

#### Scenario: 周期 Inform 设备会话活跃
- **WHEN** InformScheduler 定时触发 `triggerInform("2 PERIODIC")` 且设备 `sessionActive` 为 true
- **THEN** CAS 失败，周期 Inform 被跳过，下一个周期（`scheduleAtFixedRate`）自然恢复

### Requirement: 移除 CwmpSession.startSession() 的暴力关闭逻辑
`CwmpSession.startSession()` SHALL 不再包含 `if (state == State.IN_SESSION) { close(); }` 分支。设备级 CAS 互斥已保证不会并发调用 `startSession()`。

#### Scenario: startSession 不关闭已有会话
- **WHEN** `CwmpSession.startSession(events)` 被调用且当前 state 为 IDLE
- **THEN** 会话正常初始化为 IN_SESSION 状态，不检查或关闭任何已有会话