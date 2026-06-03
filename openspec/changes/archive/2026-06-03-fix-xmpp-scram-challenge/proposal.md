## Why

XMPP SCRAM-SHA-1 认证流程中，服务器发送 `<challenge>` 元素后客户端没有响应，导致连接挂死。当前代码只实现了 PLAIN 机制（一步完成）的流程，缺少 SCRAM-SHA-1 所需的 challenge-response 循环：`XmppStanza` 没有 `AuthChallenge`/`AuthResponse` 类型，解码器不解析 `<challenge>`，编码器不发送 `<response>`，`SaslHandler` 没有 `handleChallenge()` 方法，dispatcher 不分发 challenge。此外 `SaslHandler.startAuth()` 中 username/password 是局部变量，challenge 阶段无法复用。

## What Changes

- 在 `XmppStanza` sealed interface 中新增 `AuthChallenge` 和 `AuthResponse` record 类型，加入 permits 列表
- `XmppStreamDecoder.parseElement()` 新增 `<challenge>` XML 元素解析，产出 `AuthChallenge` stanza
- `XmppStreamEncoder.encode()` 新增 `AuthResponse` stanza 编码，发送 `<response xmlns="urn:ietf:params:xml:ns:xmpp-sasl">payload</response>`
- `SaslHandler` 新增 `handleChallenge()` 方法，调用 `selectedMechanism.getChallengeResponse()` 计算响应并发送 `AuthResponse`
- `SaslHandler` 将 username/password 从局部变量提升为实例字段，供 challenge 阶段复用
- `XmppStanzaDispatcher.dispatch()` 新增 `AuthChallenge` case，路由到 `saslHandler.handleChallenge()`

## Capabilities

### New Capabilities
- `sasl-challenge-response`: XMPP SASL challenge-response 循环处理能力，涵盖 stanza 类型定义、编解码、分发和 handler 处理

### Modified Capabilities

## Impact

- 受影响代码：`XmppStanza.java`、`XmppStreamDecoder.java`、`XmppStreamEncoder.java`、`SaslHandler.java`、`XmppStanzaDispatcher.java`
- SCRAM-SHA-1 认证从完全不可用变为可用；PLAIN 机制不受影响（其 `getChallengeResponse()` 返回 null，不会被触发）
- 无 API/配置变更，无依赖变更