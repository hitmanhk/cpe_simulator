## Context

XMPP NAT 模式下的 SASL 认证当前只支持一步完成的 PLAIN 机制。SCRAM-SHA-1 作为优先机制，需要 challenge-response 循环，但该循环在五个层面完全缺失：stanza 类型、解码器解析、编码器发送、handler 处理、dispatcher 分发。`ScramSha1Mechanism` 已实现完整 SCRAM 算法（含 `getChallengeResponse()`），但该方法从未被调用。此外 `SaslHandler.startAuth()` 中 username/password 是局部变量，challenge 到来时无法复用。

```
当前 SCRAM-SHA-1 流程（断裂）：

Client ──<auth mechanism="SCRAM-SHA-1">──▶ Server   ✅ startAuth() 发出
Server ──<challenge>──▶ Client                ❌ decoder → RawXml, dispatcher 丢弃
Client ──<response>──▶ Server                 ❌ 不存在
Server ──<success>──▶ Client                  ❌ 永远不会到达
```

## Goals / Non-Goals

**Goals:**
- 让 SCRAM-SHA-1 认证完整可用（challenge-response 循环打通）
- PLAIN 机制继续正常工作（不受影响）
- 保持与现有 Netty 管线架构一致

**Non-Goals:**
- 新增 SCRAM-SHA-256 或其他 SASL 机制
- 重构 SASL 为可插拔的多轮次框架
- 修改 XMPP 连接状态机（ConnectionState）——`AUTHENTICATING` 已足够覆盖 challenge 阶段

## Decisions

### Decision 1: AuthChallenge/AuthResponse 作为独立 record 类型

**选择**: 在 `XmppStanza` sealed interface 中新增两个独立 record：`AuthChallenge(String payload)` 和 `AuthResponse(String payload)`

**替代方案**: 扩展现有 `AuthStanza` 加一个 `type` 字段区分 auth/challenge/response

**理由**: sealed interface + pattern matching 是当前代码风格（`XmppStanzaDispatcher.dispatch()` 用 switch pattern matching）。独立类型让 dispatcher 的 switch 分支语义清晰，每个 case 对应一种 handler 方法，与 `AuthSuccess`/`AuthFailure` 的设计保持一致。给 `AuthStanza` 加 type 字段则需要在 dispatcher 里再做二次判断。

### Decision 2: SaslHandler 保存 username/password 为实例字段

**选择**: 在 `SaslHandler` 中新增 `private String authUsername` 和 `private String authPassword` 字段，`startAuth()` 中赋值

**替代方案**: 每次从 `config` 重新读取

**理由**: `startAuth()` 中 username 经过 `config.getUserPrefix() + connection.getDeviceId()` 组合计算，每次重新组合虽可但语义不如显式保存清晰。password 来自 `config.getPassword()`，保存后避免每次访问 config。`selectedMechanism` 已是实例字段，username/password 与之对称。

### Decision 3: challenge handler 中 getChallengeResponse 返回 null 视为失败

**选择**: `handleChallenge()` 中若 `getChallengeResponse()` 返回 null，将 connection 状态设为 `FAILED`

**理由**: `PlainMechanism.getChallengeResponse()` 返回 null（PLAIN 不应有 challenge），如果服务器对 PLAIN 发了 challenge 说明协议异常。`ScramSha1Mechanism.getChallengeResponse()` 内部 catch Exception 也返回 null（算法错误），都应视为认证失败。

## Risks / Trade-offs

- **[Risk] Openfire 可能发多轮 challenge** → SCRAM-SHA-1 标准只有一轮 challenge，`handleChallenge()` 设计为单轮响应。如果服务器异常发多轮，当前代码会在第二轮 challenge 时因 `clientFirstMessageBare` 已被消费而出错。→ 缓解：日志记录 warning，`getChallengeResponse()` 返回 null 时设 FAILED 状态
- **[Risk] XmppStanza permits 列表变长** → 新增两个 record 使 permits 从 12 变为 14，switch 分支增多。→ 缓解：当前规模仍可控，所有 SASL 相关类型逻辑内聚
- **[Trade-off] 不新增 CHALLENGING 状态** → challenge 阶段仍用 `AUTHENTICATING` 状态。这简化了状态机，但意味着外部观察者无法区分"发了 auth 等待 challenge"和"发了 response 等待 success"。→ 接受：对运行时管理接口（UDS）来说，两种子状态不需要区分