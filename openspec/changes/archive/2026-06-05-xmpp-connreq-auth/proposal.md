## Why

XMPP ConnectionRequest 目前无任何认证机制——任何带 `urn:broadband-forum-org:cwmp:xmppConnReq` 命名空间的 IQ 都会被直接接受并触发 Inform。与此同时，HTTP ConnectionRequest 已有 Basic Auth 保护。这导致 NAT 模式下 XMPP 路径成为未保护的后门，不符合 TR-069 协议对 ConnectionRequest 认证的要求。此外，当前使用的 XMPP ConnectionRequest 命名空间缺少 TR-069 规定的版本后缀 `-1-0`。

## What Changes

- 为 XMPP ConnectionRequest IQ 实现 TR-069 规定的 Digest 认证机制（challenge-response 流程）
- 新增 `ConnReqAuthHandler` 类处理认证逻辑：nonce 生成、challenge 构造、digest 验证
- 新增 `ConnReqAuthConfig` 配置 record，复用 `AppConfig` 的 `conn-req-user`/`conn-req-password` 凭据
- 修改 `IqHandler` 将 ConnectionRequest 处理委托给 `ConnReqAuthHandler`
- 修改 `XmppConnection` 和 `XmppEngine` 传递认证配置
- **BREAKING**: 更新 `XmlUtil.NS_CWMP_XMPP_CONN_REQ` 常量值从 `urn:broadband-forum-org:cwmp:xmppConnReq` 改为 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0`（符合 TR-069 规范）
- 认证未开启时行为不变（向后兼容）

## Capabilities

### New Capabilities
- `xmpp-connreq-digest-auth`: XMPP ConnectionRequest 的 Digest 认证，包含 challenge 发送、nonce 管理、digest 验证、认证结果 IQ 构造

### Modified Capabilities
- `xmpp-connreq-namespace`: XMPP ConnectionRequest 命名空间从无版本后缀更新为带 `-1-0` 后缀

## Impact

- **xmpp 包**: `IqHandler`、`XmppConnection`、`XmppEngine` 接口/构造函数变更
- **common 包**: `XmlUtil.NS_CWMP_XMPP_CONN_REQ` 常量值变更（**BREAKING**，所有引用此常量的代码自动跟随）
- **cwmp 包**: `DigestAuth` 被跨包引用，但不改动自身
- **engine 包**: `SimulationEngine` 构造 `ConnReqAuthConfig` 并传递给 `XmppEngine`
- **配置**: 复用现有 `conn-req-user`/`conn-req-password`，可选新增 `conn-req-algorithm`（默认 MD5）
- **CLI**: `StartCommand` 可选新增 `--conn-req-algorithm` 参数