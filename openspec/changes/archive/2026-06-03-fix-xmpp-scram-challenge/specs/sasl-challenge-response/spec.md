## ADDED Requirements

### Requirement: XmppStanza 支持 AuthChallenge 和 AuthResponse 类型

XmppStanza sealed interface SHALL 新增 `AuthChallenge(String payload)` 和 `AuthResponse(String payload)` 两个 record 类型，并加入 permits 列表。AuthChallenge 表示服务器发送的 SASL challenge 元素，payload 为 base64 编码的 challenge 数据。AuthResponse 表示客户端发送的 SASL response 元素，payload 为 base64 编码的 response 数据。

#### Scenario: 解码器产出 AuthChallenge stanza
- **WHEN** XmppStreamDecoder 收到 `<challenge xmlns="urn:ietf:params:xml:ns:xmpp-sasl">base64data</challenge>` XML 元素
- **THEN** 解码器 SHALL 产出 `XmppStanza.AuthChallenge` 类型 stanza，payload 为元素文本内容（base64 字串）

#### Scenario: 编码器发送 AuthResponse stanza
- **WHEN** XmppStreamEncoder 收到 `XmppStanza.AuthResponse` stanza
- **THEN** 编码器 SHALL 编码为 `<response xmlns="urn:ietf:params:xml:ns:xmpp-sasl">payload</response>` XML 字串并发送到网络

#### Scenario: challenge 元素为空
- **WHEN** XmppStreamDecoder 收到 `<challenge xmlns="urn:ietf:params:xml:ns:xmpp-sasl"/>`（无文本内容）
- **THEN** 解码器 SHALL 产出 `XmppStanza.AuthChannel` 类型 stanza，payload 为 null

### Requirement: SaslHandler 处理 challenge 并发送 response

SaslHandler SHALL 新增 `handleChallenge(AuthChallenge challenge)` 方法。该方法 SHALL 调用 `selectedMechanism.getChallengeResponse(challenge.payload(), authUsername, authPassword)` 计算响应。若计算结果为 null，SaslHandler SHALL 将连接状态设为 FAILED。若计算结果非 null，SaslHandler SHALL 通过 `connection.sendStanza()` 发送 `XmppStanza.AuthResponse(response)` stanza。SaslHandler SHALL 将 authUsername 和 authPassword 作为实例字段保存，在 `startAuth()` 中赋值，供 `handleChallenge()` 复用。

#### Scenario: SCRAM-SHA-1 challenge 正常响应
- **WHEN** 服务器发送 SCRAM-SHA-1 challenge 且 `ScramSha1Mechanism.getChallengeResponse()` 返回有效 response
- **THEN** SaslHandler SHALL 发送 `AuthResponse(response)` stanza，连接状态保持 AUTHENTICATING

#### Scenario: challenge 响应计算失败
- **WHEN** `getChallengeResponse()` 返回 null（算法异常或 nonce 不匹配）
- **THEN** SaslHandler SHALL 将连接状态设为 FAILED，不发送任何 stanza

#### Scenario: PLAIN 机制收到 challenge
- **WHEN** 选择 PLAIN 机制但服务器发送了 challenge（协议异常）
- **THEN** `PlainMechanism.getChallengeResponse()` 返回 null，SaslHandler SHALL 将连接状态设为 FAILED

### Requirement: XmppStanzaDispatcher 分发 AuthChallenge

XmppStanzaDispatcher 的 dispatch 方法 SHALL 新增 `AuthChallenge` case，将 challenge stanza 路由到 `saslHandler.handleChallenge()`。

#### Scenario: dispatcher 收到 AuthChallenge
- **WHEN** XmppStanzaDispatcher 收到 `XmppStanza.AuthChallenge` stanza
- **THEN** dispatcher SHALL 调用 `saslHandler.handleChallenge(challenge)`