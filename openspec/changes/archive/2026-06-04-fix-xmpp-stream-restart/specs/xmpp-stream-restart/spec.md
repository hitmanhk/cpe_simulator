## ADDED Requirements

### Requirement: Decoder reset on stream restart
XMPP 流解码器 SHALL 在每次流重启（SASL 认证成功后的流重启、StartTLS 协商成功后的流重启）时重置内部状态，将 `streamOpened` 标志设为 `false`，并清空 accumulator 缓冲区。

#### Scenario: SASL 认证成功后流重启
- **WHEN** 客户端收到 SASL `<success>` 节并即将发送新的 `<stream:stream>` 开标签
- **THEN** 解码器 SHALL 先重置 `streamOpened=false` 和 accumulator，再允许客户端发送新的流开标签

#### Scenario: StartTLS proceed 后流重启
- **WHEN** 客户端收到 `<proceed>` 并完成 TLS 协商后即将发送新的 `<stream:stream>`
- **THEN** 解码器 SHALL 先重置 `streamOpened=false` 和 accumulator

### Requirement: Stream features parsed after restart
流重启后服务器发来的 `<stream:stream>` + `<stream:features>` SHALL 被正确解析和分发，使后续协议流程（如 Resource Bind）能正常执行。

#### Scenario: SASL 后服务器发送新 stream features
- **WHEN** SASL 成功后客户端发送新的 `<stream:stream>`，服务器回应包含 `<stream:features>`（含 `<bind/>`）
- **THEN** 解码器 SHALL 将 `<stream:stream>` 解析为 `StreamOpen` stanza，`<stream:features>` 解析为 `StreamFeatures` stanza，且 `StreamFeatures.hasBind()` SHALL 返回 `true`

#### Scenario: Bind request sent after SASL restart
- **WHEN** 流重启后 `StreamFeatures` 被 `StreamHandler.handleStreamFeatures()` 处理，且 connection state 为 `AUTHENTICATED`
- **THEN** `BindHandler.startBind()` SHALL 被调用，发送 `<iq type="set"><bind/></iq>`

### Requirement: Reset invoked via pipeline access
解码器重置 SHALL 通过 Netty Channel Pipeline 的命名 handler 查找机制访问 `XmppStreamDecoder` 实例并调用其 `reset()` 方法。

#### Scenario: SaslHandler triggers decoder reset
- **WHEN** `SaslHandler.handleAuthResult()` 处理 `AuthSuccess`
- **THEN** SHALL 通过 `connection.getChannel().pipeline().get(XmppStreamDecoder.class)` 获取 decoder 并调用 `reset()`

#### Scenario: Decoder not found in pipeline
- **WHEN** pipeline 中无法获取 `XmppStreamDecoder` 实例
- **THEN** SHALL 记录警告日志但不中断认证流程