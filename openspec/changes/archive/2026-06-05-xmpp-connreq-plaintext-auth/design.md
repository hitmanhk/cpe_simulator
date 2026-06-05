## Context

当前 XMPP ConnectionRequest 认证实现基于错误的 Digest challenge-response 流程，使用 `<connReq>` 元素名和 XML 属性承载凭证。TR-069 规范只定义了明文认证：ACS 发送 `<connectionRequest>` 含 `<username>/<password>` 子元素，CPE 一步明文比对。

现有代码涉及：
- `ConnReqAuthHandler` — 两步 Digest 流程、nonce 状态管理、challenge 生成
- `ConnReqAuthConfig` — 包含无意义的 `algorithm` 字段
- `AppConfig` / `StartCommand` — `conn-req-algorithm` 配置项
- `DigestAuth` (cwmp 包) — 被 ConnReqAuthHandler 引用

Public 模式（HTTP）的 ConnectionRequest 认证（`CwmpServerHandler`）已经在做明文比对，无需改动。

## Goals / Non-Goals

**Goals:**
- 实现符合 TR-069 规范的 XMPP ConnectionRequest 明文认证
- 删除所有 Digest 相关代码（nonce 状态、challenge 生成、DigestAuth 引用）
- 清理配置层 `algorithm` 相关代码
- 更新所有受影响的测试

**Non-Goals:**
- 不改动 Public 模式（HTTP）的 ConnectionRequest 认证
- 不改动 `XmppStanza.IqStanza` 的 record 结构
- 不改动 `XmppStreamDecoder.parseIq()` 的解析逻辑（namespace 路由仍然正常工作）
- 不支持 Digest 模式作为向后兼容选项（规范没有定义 Digest）

## Decisions

### 1. 凭证提取方式：从 rawXml 解析子元素文本

**选择**: `ConnReqAuthHandler` 从 `IqStanza.rawXml()` 中解析 `<username>xxx</username>` 和 `<password>xxx</password>` 子元素文本

**替代方案**: 扩展 `IqStanza` record 添加 username/password 字段，在 `XmppStreamDecoder` 解析时提取

**理由**: 路径B 更轻量——ConnReqAuthHandler 已有从 rawXml 提取数据的模式（`parseConnReqAttrs`），改为提取子元素文本即可。避免修改 `IqStanza` record 和 `XmppStreamDecoder`，减少波及范围。`<connectionRequest>` 的 namespace 与 `<connReq>` 相同，`IqHandler` 的 namespace 路由不受影响。

### 2. 错误响应包含原始请求体

**选择**: 认证失败时，error IQ 中包含原始 `<connectionRequest>` 内容（子元素文本）

**理由**: TR-069 规范要求错误响应回显 ACS 发送的请求体。当前 `sendAuthError()` 不含请求体，需重构。

### 3. error type 使用 "cancel"

**选择**: 认证失败 `<error type="cancel">`，而非当前的 `type="modify"`

**理由**: 规范定义的失败响应使用 `type="cancel"`。Challenge 响应不再存在（无两步流程），所以 `type="modify"` 也不再需要。

## Risks / Trade-offs

- **[明文传输]** → XMPP ConnectionRequest 在 TLS 通道上传输，明文密码在 TLS 保护下传递，安全性由 TLS 层保障。这与 Public 模式 HTTP ConnectionRequest（同样明文、依赖 TLS）一致。
- **[向后兼容]** → 删除 Digest 流程是 BREAKING 变更，但当前实现本就不符合规范，不存在"兼容正确实现"的问题。如果之前有 ACS 确实按当前代码的 Digest 方式对接，那该 ACS 本身也不符合 TR-069 规范。