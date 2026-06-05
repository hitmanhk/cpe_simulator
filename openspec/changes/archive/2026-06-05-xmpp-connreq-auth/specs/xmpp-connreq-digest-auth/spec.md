## ADDED Requirements

### Requirement: XMPP ConnectionRequest Digest 认证挑战
当 XMPP ConnectionRequest 认证开启时，CPE 收到不带认证属性（username/response）的 connReq IQ-get，MUST 发送包含 challenge 的 IQ-error 响应。challenge MUST 包含 algorithm、nonce 和 realm 属性。nonce MUST 每次挑战时随机生成。realm MUST 默认取 XMPP domain 配置值。IQ-error MUST 包含 type="modify" 和 `<not-authorized xmlns="urn:ietf:params:xml:ns:xmpp-stanzas"/>` 错误条件。

#### Scenario: 收到无认证属性的 connReq IQ 触发挑战
- **WHEN** 认证开启，CPE 收到 `<connReq xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0"/>` IQ-get，且 connReq 元素无 username/response 属性
- **THEN** CPE 发送 IQ-error，包含 `<connReq>` 子元素内的 `<challenge algorithm="..." nonce="..." realm="..."/>` 和 `<error type="modify"><not-authorized/></error>`，同时存储 nonce 及其时间戳用于后续验证

#### Scenario: 认证未开启时不触发挑战
- **WHEN** 认证未开启（conn-req-user 或 conn-req-password 为空），CPE 收到 connReq IQ-get
- **THEN** CPE 直接回复 IQ-result 并触发 Inform callback，行为与认证功能添加前完全一致

### Requirement: XMPP ConnectionRequest Digest 认证验证成功
CPE 收到带认证属性的 connReq IQ-get，MUST 使用 `DigestAuth.calculateDigestResponse()` 验证 response。验证参数：method MUST 为 "GET"，digest-uri MUST 为 IQ "to" 属性值（CPE JID），若 IQ "to" 为 null 则 fallback 使用 connection.getJid()。验证通过后 MUST 发送 IQ-result 并触发 Inform callback。验证时 MUST 清除存储的 nonce。

#### Scenario: 正确的 digest response 验证成功
- **WHEN** 认证开启，CPE 收到带 username、response、nonce、realm、algorithm、qop、nc、cnonce 属性的 connReq IQ-get，且 nonce 匹配存储的 pendingNonce 且未过期，且 response 与用相同参数计算的期望值一致
- **THEN** CPE 发送 IQ-result，清除 pendingNonce，并触发 Inform callback

#### Scenario: IQ to 属性用作 digest-uri
- **WHEN** 验证 digest 时 IQ "to" 属性为 "cpe001@iot.example.com/sim"
- **THEN** digest-uri MUST 为 "cpe001@iot.example.com/sim"

#### Scenario: IQ to 属性为 null 时 fallback
- **WHEN** 验证 digest 时 IQ "to" 属性为 null，且 connection.getJid() 为 "cpe001@iot.example.com/sim"
- **THEN** digest-uri MUST 为 "cpe001@iot.example.com/sim"

### Requirement: XMPP ConnectionRequest Digest 认证验证失败
CPE 收到带认证属性的 connReq IQ-get 且 digest response 不匹配期望值，MUST 发送 IQ-error（type="modify"，not-authorized），MUST 清除存储的 nonce，MUST NOT 触发 Inform callback。

#### Scenario: 错误的 digest response 验证失败
- **WHEN** 认证开启，CPE 收到带认证属性的 connReq IQ-get，且 response 与期望值不一致
- **THEN** CPE 发送 IQ-error（type="modify"，not-authorized），清除 pendingNonce，不触发 Inform callback

### Requirement: XMPP ConnectionRequest nonce 生命周期管理
nonce MUST per-connection 存储（volatile 字段）。nonce 过期时间 MUST 为 5 分钟。过期后收到认证请求 MUST NOT 直接拒绝，MUST 发送包含新 challenge 的 IQ-error 让 ACS 重新走认证流程。收到不匹配的 nonce（从未发出过的 nonce）MUST 直接发送 IQ-error not-authorized，MUST NOT 重新 challenge。

#### Scenario: nonce 过期后重新挑战
- **WHEN** 认证开启，CPE 收到带认证属性的 connReq IQ-get，nonce 匹配存储的 pendingNonce 但超过 5 分钟已过期
- **THEN** CPE 发送包含新 challenge 的 IQ-error，生成新 nonce 替换 pendingNonce，不触发 Inform callback

#### Scenario: nonce 不匹配直接拒绝
- **WHEN** 认证开启，CPE 收到带认证属性的 connReq IQ-get，nonce 不匹配存储的 pendingNonce（即 nonce 值不同）
- **THEN** CPE 发送 IQ-error（type="modify"，not-authorized），不生成新 challenge，不触发 Inform callback

#### Scenario: 从未发起过 challenge 时收到认证请求
- **WHEN** 认证开启，pendingNonce 为 null，CPE 收到带认证属性的 connReq IQ-get
- **THEN** CPE 发送包含新 challenge 的 IQ-error，生成新 nonce

### Requirement: connReq IQ 属性解析
CPE MUST 从 `IqStanza.rawXml` 中截取 `<connReq>` 元素子串，使用正则提取属性（username、response、nonce、realm、algorithm、qop、nc、cnonce）。判断是否带认证 MUST 检查 username 和 response 属性是否存在。

#### Scenario: 解析带认证属性的 connReq IQ
- **WHEN** CPE 收到 IQ rawXml 包含 `<connReq xmlns="..." username="admin" response="abc" nonce="..." realm="..." algorithm="MD5"/>`
- **THEN** MUST 提取所有认证相关属性为 Map，且 username 和 response 不为 null

#### Scenario: 解析无认证属性的 connReq IQ
- **WHEN** CPE 收到 IQ rawXml 包含 `<connReq xmlns="..."/>`
- **THEN** username 和 response 属性 MUST 为 null，判定为首次请求需 challenge

### Requirement: 支持 MD5 和 SHA-256 算法
Digest 认证 MUST 支持 algorithm 为 "MD5" 和 "SHA-256"。算法选择 MUST 由配置决定（默认 MD5）。challenge 中 MUST 包含 algorithm 属性。验证时 MUST 使用 challenge 中指定的算法。

#### Scenario: SHA-256 算法验证
- **WHEN** conn-req-algorithm 配置为 "SHA-256"，CPE 发出 challenge 指定 algorithm="SHA-256"，ACS 用 SHA-256 计算 response
- **THEN** CPE 验证 MUST 使用 SHA-256 计算期望值并比较