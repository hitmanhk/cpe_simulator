## Context

当前 XMPP ConnectionRequest 无认证保护。`IqHandler.handleConnectionRequest()` 收到任何带 `urn:broadband-forum-org:cwmp:xmppConnReq` 命名空间的 IQ 即直接回复 IQ-result 并触发 Inform callback。HTTP ConnectionRequest 路径已有 Basic Auth（`CwmpServerHandler.isAuthorized()`），但 XMPP 路径完全裸奔。命名空间也缺少 TR-069 规定的版本后缀 `-1-0`。

xmpp 包现有模式是每个协议关注点有独立 Handler：StreamHandler、SaslHandler、BindHandler、PresenceHandler、PingHandler、IqHandler。IqHandler 作为 IQ 调度器，不应承担认证验证职责。

`DigestAuth`（cwmp 包）已实现完整的 HTTP Digest 算法（`calculateDigestResponse`、`hashHex`），支持 MD5 和 SHA-256。核心公式可直接复用，但 HTTP Header 的编解码（`buildAuthorizationHeader`、`parseWwwAuthenticate`）不适用于 XMPP 场景。

## Goals / Non-Goals

**Goals:**
- 实现 TR-069 规定的 XMPP ConnectionRequest Digest 认证（challenge-response 流程）
- CPE 收到无认证属性的 connReq IQ → 发 challenge IQ-error
- CPE 收到带认证属性的 connReq IQ → 验证 digest → 成功回复 IQ-result + 触发 Inform，失败回复 IQ-error
- nonce 管理：per-connection 存储，5 分钟过期
- 认证未开启时行为完全不变（向后兼容）
- 更新命名空间为 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0`

**Non-Goals:**
- 不改动 HTTP ConnectionRequest 认证机制（Basic Auth 保持不变）
- 不改动 `DigestAuth` 类本身（跨包引用即可）
- 不改动 `XmppStanza` record 定义或 `XmppStreamDecoder.parseIq()`
- 不实现 ACS 侧逻辑（我们是 CPE simulator）
- 不支持 Digest 认证以外的 XMPP connReq 认证方式

## Decisions

### D1: 认证逻辑放在独立 `ConnReqAuthHandler` 类

**选择**: 新建 `xmpp/ConnReqAuthHandler.java`

**理由**: 遵循 xmpp 包现有模式——每个协议关注点有独立 Handler。认证涉及 nonce 管理、challenge 构造、digest 验证、IQ stanza 构造，职责复杂，不适合塞进 IqHandler（它只是 IQ 调度器）。

**替代方案**: 把认证逻辑直接加进 IqHandler——职责膨胀，不符合包内风格。

### D2: 凭据复用 `AppConfig` 的 `conn-req-user`/`conn-req-password`

**选择**: HTTP 和 XMPP ConnectionRequest 共用同一套凭据，通过 `ConnReqAuthConfig` record 传递。

**理由**: TR-069 规定 ConnectionRequest 凭据不分传输方式。`isConnReqAuthEnabled()` 的判断逻辑（`connReqUser != null && connReqPassword != null`）对两条路同样适用。

**替代方案**: 在 `XmppConfig` 内加独立凭据字段——配置冗余，用户要填两遍相同的用户名密码。

### D3: 配置传递方式——新增 `ConnReqAuthConfig` record

**选择**: 新建 `xmpp/ConnReqAuthConfig(boolean enabled, String username, String password, String algorithm)` record，由 `XmppEngine` 从 `AppConfig` 构造后传入 `XmppConnection`。

**理由**: `XmppConnection` 当前只接收 `XmppConfig`，不含认证凭据。record 是最轻量的传递方式，不污染 `XmppConfig` 或 `AppConfig` 的接口。

**传递路径**: `AppConfig` → `SimulationEngine` 构造 `ConnReqAuthConfig` → `XmppEngine.createConnection()` → `XmppConnection` → `connect()` 中创建 `ConnReqAuthHandler`

### D4: connReq 属性从 rawXml 中解析

**选择**: 在 `ConnReqAuthHandler` 中从 `IqStanza.rawXml` 截取 `<connReq>` 子串，用 `XmppStreamDecoder.extractAttribute()` 提取属性。

**理由**: `IqStanza` record 已有 `rawXml` 字段保留完整 IQ XML。不需要扩展 record 定义或修改 decoder。`extractAttribute` 是已有的正则属性提取方法，对任何 XML 子串都适用。

**替代方案**: 在 `IqStanza` 中新增 `connReqAttributes` Map 字段——需要改 record 定义、decoder 解析逻辑、encoder 编码逻辑，改动面过大。

### D5: digest-uri 使用 IQ "to" 属性（CPE JID）

**选择**: `digest-uri = iq.to()`，fallback `connection.getJid()`。`method = "GET"`。

**理由**: TR-069 XMPP ConnectionRequest 的惯例是 IQ type "get" → method GET，IQ "to" 属性（CPE JID）→ digest-uri。ACS 和 CPE 不需要额外属性字段来约定，隐含在 IQ stanza 上下文里。

### D6: nonce 过期后重新 challenge，nonce 不匹配直接拒绝

**选择**:
- `pendingNonce == null` → 从未 challenge，发新 challenge
- `nonce != pendingNonce` → nonce 不匹配（可能是攻击），返回 IQ-error not-authorized，**不重新 challenge**
- `nonce == pendingNonce && 已过期` → 返回包含新 challenge 的 IQ-error，让 ACS 重新走流程

**理由**: 区分过期重试和恶意请求。过期是正常场景应友好重试；不匹配的 nonce 说明对方在用从未发出的 nonce，可能是攻击，应直接拒绝。

### D7: realm 默认用 XmppConfig.domain

**选择**: challenge 中的 realm 默认取 `XmppConfig.domain`（如 `iot.example.com`）。

**理由**: XMPP domain 和 Digest realm 概念一致——都标识认证域。如需不同 realm 可后续加配置覆盖，默认用 domain 最自然。

## Risks / Trade-offs

- **[BREAKING 命名空间变更]** → `NS_CWMP_XMPP_CONN_REQ` 从 `xmppConnReq` 改为 `xmppConnReq-1-0`，所有引用自动跟随。如果 ACS 使用旧命名空间发送 connReq IQ，CPE 将不再识别。缓解：TR-069 规定 ACS 必须使用版本化命名空间，旧命名空间本身就是不规范用法。

- **[IQ stanza 构造用 RawXml]** → challenge/error IQ 手动拼 XML，无 schema 验证。缓解：与现有 `IqHandler.sendError()` 方式一致（都是 StringBuilder 拼 XML），是项目既定风格。

- **[volatile nonce 字段无原子性保障]** → 极端场景下同一个连接的两个 IQ 可能并发处理。缓解：`XmppStanzaDispatcher` 用 `virtualThreadExecutor.execute()` 调度，但 Netty pipeline 保证同一 channel 的 inbound 事件有序。challenge 和后续 verify 请求不会并发到达。

- **[digest-uri 用 iq.to() 可能被伪造]** → 恶意 ACS 可以在 IQ 中设置不同的 "to" 属性来操纵 digest 计算。缓解：XMPP 服务器应验证 IQ "to" 与接收者 JID 一致；CPE 侧 fallback 用 `connection.getJid()` 可作为交叉验证。