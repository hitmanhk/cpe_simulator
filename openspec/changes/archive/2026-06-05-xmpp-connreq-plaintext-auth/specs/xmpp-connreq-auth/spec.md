## ADDED Requirements

### Requirement: XMPP ConnectionRequest 明文认证
当 CPE 收到 ACS 发送的 `<connectionRequest>` IQ（namespace `urn:broadband-forum-org:cwmp:xmppConnReq-1-0`），CPE SHALL 从 IQ 子元素中提取 `<username>` 和 `<password>` 的文本内容，与本地配置的 `conn-req-user` 和 `conn-req-password` 进行明文比对。认证成功返回 `type="result"` IQ，认证失败返回 `type="error"` IQ 含原始 `<connectionRequest>` 请求体和 `<error type="cancel"><not-authorized/></error>`。

#### Scenario: 认证禁用时直接接受
- **WHEN** `conn-req-auth-enabled` 为 false，ACS 发送 `<connectionRequest>` IQ
- **THEN** CPE 返回 `<iq type="result" id="{iq.id}" to="{iq.from}"/>` 并触发 connectionRequestCallback

#### Scenario: 认证启用且凭证匹配
- **WHEN** `conn-req-auth-enabled` 为 true，ACS 发送 `<connectionRequest>` IQ 含 `<username>{configUser}</username>` 和 `<password>{configPassword}</password>`，且与配置值一致
- **THEN** CPE 返回 `<iq type="result" id="{iq.id}" to="{iq.from}"/>` 并触发 connectionRequestCallback

#### Scenario: 认证启用且用户名不匹配
- **WHEN** `conn-req-auth-enabled` 为 true，ACS 发送 `<connectionRequest>` IQ 含 `<username>wrong</username>` 和 `<password>{configPassword}</password>`
- **THEN** CPE 返回 `<iq type="error" id="{iq.id}" to="{iq.from}">` 含原始 `<connectionRequest>` 子元素内容 + `<error type="cancel"><not-authorized xmlns="urn:ietf:params:xml:ns:xmpp-stanzas"/></error></iq>`，不触发 callback

#### Scenario: 认证启用且密码不匹配
- **WHEN** `conn-req-auth-enabled` 为 true，ACS 发送 `<connectionRequest>` IQ 含 `<username>{configUser}</username>` 和 `<password>wrong</password>`
- **THEN** CPE 返回 `<iq type="error" id="{iq.id}" to="{iq.from}">` 含原始 `<connectionRequest>` 子元素内容 + `<error type="cancel"><not-authorized/></error></iq>`，不触发 callback

#### Scenario: 认证启用但 IQ 不含 username/password 子元素
- **WHEN** `conn-req-auth-enabled` 为 true，ACS 发送 `<connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0"/>`（空元素，无子元素）
- **THEN** CPE 返回 `<iq type="error">` 含 `<connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0"/>` + `<error type="cancel"><not-authorized/></error></iq>`，不触发 callback

### Requirement: 删除 Digest challenge-response 流程
CPE SHALL NOT 在 XMPP ConnectionRequest 认证中使用 Digest challenge-response 流程。不再发送 `<challenge>` 元素，不再维护 nonce 状态，不再引用 `DigestAuth` 计算逻辑。

#### Scenario: 收到含 Digest 属性的 connReq 不再触发 challenge
- **WHEN** ACS 发送 `<connReq>` IQ 含 `username` `response` `nonce` 等属性
- **THEN** CPE 不会返回 `<challenge>` 响应。此类 IQ 的 namespace 匹配 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0` 但元素结构不符合规范，CPE 按不含凭证处理，返回认证失败 error

### Requirement: 删除 ConnReqAuthConfig.algorithm
`ConnReqAuthConfig` record SHALL 只包含 `enabled`、`username`、`password` 三个字段。`algorithm` 字段及相关配置（`conn-req-algorithm` YAML 属性、CLI `--conn-req-algorithm` 选项、`AppConfig.getConnReqAlgorithm()`）SHALL 全部删除。

#### Scenario: ConnReqAuthConfig 只接受三个参数
- **WHEN** 创建 `ConnReqAuthConfig` 实例
- **THEN** 构造参数为 `(boolean enabled, String username, String password)`，不含 algorithm 参数