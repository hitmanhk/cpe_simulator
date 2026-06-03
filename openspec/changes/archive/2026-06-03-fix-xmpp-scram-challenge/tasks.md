## 1. Stanza 类型层

- [x] 1.1 在 `XmppStanza` sealed interface 新增 `AuthChallenge(String payload)` record，加入 permits 列表
- [x] 1.2 在 `XmppStanza` sealed interface 新增 `AuthResponse(String payload)` record，加入 permits 列表

## 2. 编解码层

- [x] 2.1 在 `XmppStreamDecoder.parseElement()` 新增 `<challenge>` XML 元素解析分支：匹配 `<challenge` + `xmlns="urn:ietf:params:xml:ns:xmpp-sasl"`，提取文本内容为 payload，产出 `AuthChallenge` stanza
- [x] 2.2 在 `XmppStreamEncoder.encode()` switch 新增 `AuthResponse` case：编码为 `<response xmlns="urn:ietf:params:xml:ns:xmpp-sasl">payload</response>`，payload 为 null 时发送 `<response xmlns="urn:ietf:params:xml:ns:xmpp-sasl"/>`

## 3. Handler 层

- [x] 3.1 在 `SaslHandler` 新增 `private String authUsername` 和 `private String authPassword` 实例字段
- [x] 3.2 在 `SaslHandler.startAuth()` 中将组合后的 username 和 password 赋值给实例字段
- [x] 3.3 在 `SaslHandler` 新增 `handleChallenge(AuthChallenge challenge)` 方法：调用 `selectedMechanism.getChallengeResponse(challenge.payload(), authUsername, authPassword)`，结果为 null 则设状态 FAILED，非 null 则发送 `AuthResponse` stanza
- [x] 3.4 为 `handleChallenge` 方法增加日志：记录收到 challenge 和发送 response（或失败）

## 4. Dispatcher 层

- [x] 4.1 在 `XmppStanzaDispatcher.dispatch()` switch 新增 `AuthChallenge` case，路由到 `saslHandler.handleChallenge(challenge)`

## 5. 验证

- [x] 5.1 编写单元测试验证 `XmppStreamDecoder` 解析 `<challenge>` 元素
- [x] 5.2 编写单元测试验证 `XmppStreamEncoder` 编码 `AuthResponse` stanza
- [x] 5.3 编写单元测试验证 `SaslHandler.handleChallenge()` 正常路径
- [x] 5.4 编写单元测试验证 `SaslHandler.handleChallenge()` 失败路径
- [x] 5.5 运行 `mvn test` 确保所有测试通过