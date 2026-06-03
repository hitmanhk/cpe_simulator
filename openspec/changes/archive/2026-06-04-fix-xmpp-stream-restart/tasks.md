## 1. Decoder 重置机制

- [x] 1.1 在 `SaslHandler.handleAuthResult()` 中，处理 `AuthSuccess` 时通过 `connection.getChannel().pipeline().get(XmppStreamDecoder.class)` 获取 decoder 实例
- [x] 1.2 获取到 decoder 后调用 `reset()`，获取不到则记录 WARN 日志
- [x] 1.3 确保 `reset()` 在 `sendStanza(StreamOpen)` **之前**调用，顺序为：setState → reset → sendStanza

## 2. StartTLS 流重启支持

- [x] 2.1 在 `XmppStreamDecoder` 或相关 handler 中，处理 `<proceed>` 后同样触发 decoder `reset()`（如果当前有 StartTLS 处理逻辑）— **N/A**: StartTLS 流程尚未实现，`StartTlsProceed` 在 dispatcher 中走 default 分支
- [x] 2.2 确认当前代码是否已实现 StartTLS 流程；如未实现，此任务标记为 N/A — **确认**: StartTLS 流程未实现，仅解析 stanza 但无 handler 逻辑

## 3. 单元测试

- [x] 3.1 编写测试验证 `XmppStreamDecoder.reset()` 能正确重置 `streamOpened=false` 和清空 accumulator
- [x] 3.2 编写测试验证 SASL success 后，第二次 `<stream:stream>` + `<stream:features>` 能被正确解析为 `StreamOpen` 和 `StreamFeatures` stanza
- [x] 3.3 编写测试验证 `SaslHandler.handleAuthResult()` 在发送 `StreamOpen` 前调用了 decoder 的 `reset()``

## 4. 集成测试验证

- [x] 4.1 运行现有集成测试 `NatModeIntegrationTest`，确认 XMPP NAT 模式设备能完成 SASL → Bind → Online 全流程
- [x] 4.2 运行 `mvn test` 确认全部测试通过 — 唯一失败 `CliCommandTest.testDaemonHelperPidOperations` 是预存问题，与本次修改无关；所有 XMPP 测试 24/24 通过，NatModeIntegrationTest 5/5 通过