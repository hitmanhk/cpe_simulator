## 1. 配置与常量

- [x] 1.1 更新 `XmlUtil.NS_CWMP_XMPP_CONN_REQ` 常量值从 `urn:broadband-forum-org:cwmp:xmppConnReq` 改为 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0`
- [x] 1.2 在 `xmpp` 包新建 `ConnReqAuthConfig` record：`(boolean enabled, String username, String password, String algorithm)`，algorithm 默认 "MD5"
- [x] 1.3 在 `AppConfig` 新增 `conn-req-algorithm` 字段（默认 "MD5"），getter/setter
- [x] 1.4 在 `StartCommand` 新增 `--conn-req-algorithm` CLI 参数，映射到 AppConfig

## 2. ConnReqAuthHandler 核心实现

- [x] 2.1 在 `xmpp` 包新建 `ConnReqAuthHandler` 类：构造函数接收 `XmppConnection` 和 `ConnReqAuthConfig`
- [x] 2.2 实现 nonce 状态管理：`pendingNonce`、`pendingRealm`、`pendingAlgorithm`、`nonceTimestamp` volatile 字段；`generateNonce()` 方法（UUID → hex）；`isNonceExpired()` 方法（5 分钟阈值）；`clearNonce()` 方法
- [x] 2.3 实现 `parseConnReqAttrs(String rawXml)` 方法：定位 `<connReq` 元素子串，用 `XmppStreamDecoder.extractAttribute()` 提取 username、response、nonce、realm、algorithm、qop、nc、cnonce 属性为 Map
- [x] 2.4 实现 challenge 逻辑：生成 nonce，构造 IQ-error XML（connReq + challenge 子元素 + error not-authorized），通过 `connection.sendStanza(RawXml)` 发送
- [x] 2.5 实现 verify 逻辑：检查 nonce 匹配与过期；用 `DigestAuth.calculateDigestResponse()` 计算期望 response（method="GET"，digest-uri=iq.to() fallback connection.getJid()）；比较结果；成功 → IQ-result + callback；失败 → IQ-error not-authorized + 清除 nonce
- [x] 2.6 实现 nonce 不匹配与过期区分：不匹配 → 直接 IQ-error not-authorized（不重新 challenge）；过期 → 发新 challenge IQ-error；pendingNonce 为 null → 发新 challenge
- [x] 2.7 实现 `handle(IqStanza iq, Consumer<String> callback)` 入口方法：authConfig.enabled → 委托认证逻辑；未开启 → 直接 IQ-result + callback（旧逻辑）

## 3. IqHandler 与 XmppConnection 集成

- [x] 3.1 修改 `IqHandler` 构造函数：新增 `ConnReqAuthHandler` 参数
- [x] 3.2 修改 `IqHandler.handleConnectionRequest()`：委托给 `connReqAuthHandler.handle(iq, connectionRequestCallback)`
- [x] 3.3 修改 `XmppConnection` 构造函数：新增 `ConnReqAuthConfig` 参数
- [x] 3.4 修改 `XmppConnection.connect()`：创建 `ConnReqAuthHandler(connection, authConfig)`，传入 `IqHandler` 构造
- [x] 3.5 修改 `XmppEngine.createConnection()`：从 `AppConfig` 构造 `ConnReqAuthConfig`，传入 `XmppConnection`
- [x] 3.6 修改 `SimulationEngine` XMPP 路初始化部分：确保 `AppConfig` 的 conn-req 凭据和 algorithm 正确传递到 `XmppEngine`

## 4. 测试

- [x] 4.1 新建 `ConnReqAuthHandlerTest`：测试 auth 未开启 → 直接 ACCEPTED；无 auth 属性 → CHALLENGED + XML 格式验证；正确 response → ACCEPTED + callback 触发；错误 response → REJECTED；nonce 过期 → RECHALLENGED；nonce 不匹配 → REJECTED；pendingNonce null → CHALLENGED；SHA-256 验证；qop=null 简化公式；iq.to() null fallback
- [x] 4.2 在 `XmppStreamDecoderTest` 追加测试：解析带 auth 属性的 connReq IQ → IqStanza 正确；解析无属性 connReq IQ → IqStanza 正确
- [x] 4.3 在 `IqHandlerTest`（如存在）追加：auth disabled 时 connReq IQ → 直接回调；auth enabled 时 → 委托给 authHandler
- [x] 4.4 运行全部测试 `mvn test` 确保无回归

## 5. 验证

- [x] 5.1 更新所有引用旧命名空间的测试用例中的 XML 字符串，确保使用 `xmppConnReq-1-0`
- [x] 5.2 确认 `NatModeIntegrationTest` XMPP ConnectionRequest 相关路径正确
- [x] 5.3 运行 `mvn clean package` 确保 JAR 构建成功