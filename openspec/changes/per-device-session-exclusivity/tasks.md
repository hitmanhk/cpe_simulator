## 1. CpeDevice 核心改动

- [x] 1.1 在 `CpeDevice` 中新增 `AtomicBoolean sessionActive` 字段，构造函数中初始化为 `false`
- [x] 1.2 修改 `triggerInform(String eventCode, String commandKey)`：在 OFFLINE 检查后新增 `sessionActive.compareAndSet(false, true)` CAS 检查；CAS 失败时直接 return 跳过，不提交 scheduler 任务
- [x] 1.3 修改 `triggerInform` 中 scheduler.execute 的 lambda：在 `runInformSession()` 外层的 try/finally 中添加 `sessionActive.set(false)` 释放，确保无论成功、异常、中断都会释放锁
- [x] 1.4 新增 `boolean isSessionActive()` 公共方法，供外部 handler 检查设备会话状态

## 2. CwmpSession 暴力关闭逻辑移除

- [x] 2.1 修改 `CwmpSession.startSession()`：移除 `if (state == State.IN_SESSION) { close(); }` 分支，只保留正常初始化逻辑

## 3. HTTP Connection Request handler 改造

- [x] 3.1 修改 `SimulationEngine.java` 中 `cwmpServer` 的 ConnectionRequestHandler lambda：添加 `device.isSessionActive()` 检查；活跃时返回 `ConnectionRequestResult(true, 200, "OK")` 但不调用 `triggerInform()`；空闲时正常调用 `triggerInform("6 CONNECTION REQUEST")`

## 4. XMPP Connection Request handler 改造

- [x] 4.1 修改 `SimulationEngine.java` 中 `xmppEngine` 的 lambda：添加 `device.isSessionActive()` 检查；活跃时不调用 `triggerInform()`；空闲时正常调用
- [x] 4.2 修改 `ConnectionRequestIntegration.handleXmppConnectionRequest()`：同样添加 `sessionActive` 检查逻辑

## 5. DeviceRegistry 改造

- [x] 5.1 修改 `DeviceRegistry.handleConnectionRequest()`：将 `session.getState() != CwmpSession.State.IDLE` 检查替换为 `device.isSessionActive()`（覆盖"活跃"和"待启动"两种状态）；活跃时返回 200 OK 不触发 Inform

## 6. 测试适配

- [x] 6.1 新增 `CpeDeviceTriggerInformTest` 测试：验证 CAS 成功触发 Inform、CAS 失败跳过触发、会话结束后释放 sessionActive、semaphore 获取失败后释放 sessionActive
- [x] 6.2 新增测试：验证 HTTP Connection Request handler 在设备会话活跃时返回 200 OK 不触发 Inform
- [x] 6.3 新增测试：验证 XMPP Connection Request handler 在设备会话活跃时不触发 Inform
- [x] 6.4 修改现有 `ProcessAcsFaultTest` 等测试：适配 `sessionActive` 互斥机制（确保测试中设备状态正确）
- [x] 6.5 运行全量测试 `mvn test` 验证无回归