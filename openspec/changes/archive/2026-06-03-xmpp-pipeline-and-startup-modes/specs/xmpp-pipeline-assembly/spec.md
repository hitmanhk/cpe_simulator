## ADDED Requirements

### Requirement: XMPP 连接完成完整握手流程
XmppConnection 建立 TCP 连接后 SHALL 自动执行完整的 XMPP 握手：Stream Open → SASL 认证 → 资源绑定 → Presence Online。

#### Scenario: 成功完成 XMPP 握手
- **WHEN** XmppConnection.connect() 被调用且 XMPP 服务器正常响应
- **THEN** 连接状态依次经过 CONNECTING → AUTHENTICATING → AUTHENTICATED → BINDING → BOUND → ONLINE

#### Scenario: SASL 认证失败
- **WHEN** XMPP 服务器返回 SASL failure
- **THEN** 连接状态变为 FAILED，不再尝试后续步骤

#### Scenario: 资源绑定失败
- **WHEN** XMPP 服务器返回 bind error IQ
- **THEN** 连接状态变为 FAILED

### Requirement: XmppStanzaDispatcher 注册到 Netty pipeline
XmppConnection.connect() SHALL 创建 XmppStanzaDispatcher 并添加到 Netty ChannelPipeline 中，位于 Decoder 和 Encoder 之后。

#### Scenario: Stanza 被正确分发
- **WHEN** XMPP 服务器发送 IQ stanza
- **THEN** XmppStanzaDispatcher 将 stanza 分发到 IqHandler 处理

#### Scenario: Stream Features 触发认证或绑定
- **WHEN** XMPP 服务器发送 stream:features 且连接未认证
- **THEN** StreamHandler 调用 SaslHandler 开始认证
- **WHEN** XMPP 服务器发送 stream:features 且连接已认证
- **THEN** StreamHandler 调用 BindHandler 开始绑定

### Requirement: connectionRequestCallback 传递到 IqHandler
XmppEngine SHALL 将 connectionRequestCallback 传递到每个 XmppConnection，XmppConnection SHALL 将其注入 IqHandler。

#### Scenario: ACS 通过 XMPP 发起连接请求
- **WHEN** XMPP 服务器发送带有 cwmp:xmppConnReq namespace 的 IQ get stanza
- **THEN** IqHandler 回复 IQ result 并调用 connectionRequestCallback，传入 deviceId

#### Scenario: callback 为 null 时不抛异常
- **WHEN** connectionRequestCallback 为 null 且收到连接请求 IQ
- **THEN** IqHandler 正常回复 IQ result，不调用任何回调

### Requirement: Handler 实例与连接一一对应
每个 XmppConnection SHALL 拥有独立的 Handler 实例集（StreamHandler、SaslHandler、BindHandler、PresenceHandler、IqHandler、PingHandler），不跨连接共享。

#### Scenario: 多连接独立运行
- **WHEN** 两个 XmppConnection 同时连接
- **THEN** 各自的 Handler 独立处理 stanza，状态互不干扰

### Requirement: 握手完成后自动发起 Stream Open
XmppConnection 在 TCP 连接建立后 SHALL 自动发送初始 stream:stream 标签以启动 XMPP 握手。

#### Scenario: TCP 连接成功后自动发起 stream
- **WHEN** TCP 连接到 XMPP 服务器成功
- **THEN** StreamHandler.openStream() 被调用，发送 stream:stream 到服务器
