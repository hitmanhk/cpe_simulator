## Why

XMPP ConnectionRequest 认证实现错误——当前使用 Digest challenge-response 两步流程（`<connReq>` 属性 + `<challenge>` 子元素），但 TR-069 规范只定义了明文认证：ACS 发送 `<connectionRequest>` 含 `<username>/<password>` 子元素，CPE 一步明文比对。当前实现与规范不符，导致无法与真实 ACS 互通。

## What Changes

- **BREAKING**: 删除整个 Digest challenge-response 认证流程（nonce 状态、challenge 生成、Digest 计算）
- 将 `<connReq>` 元素名改为 `<connectionRequest>`
- 将认证方式从 Digest 两步改为明文一步比对（从 `<username>` `<password>` 子元素提取凭证，与配置值直接比较）
- 认证失败时，错误响应需包含原始 `<connectionRequest>` 请求体，且 `<error type>` 改为 `cancel`（而非 `modify`）
- 删除 `ConnReqAuthConfig.algorithm` 字段及配置层 `conn-req-algorithm` 相关代码
- 删除 `ConnReqAuthHandler` 对 `DigestAuth` 的依赖

## Capabilities

### New Capabilities
- `xmpp-connreq-auth`: XMPP ConnectionRequest 明文认证——定义 ACS 发送 `<connectionRequest>` IQ 后 CPE 的认证比对逻辑和响应格式

### Modified Capabilities

## Impact

- `ConnReqAuthHandler.java` — 核心逻辑重写
- `ConnReqAuthConfig.java` — 去掉 algorithm 字段
- `AppConfig.java` — 删除 connReqAlgorithm 字段及 getter/setter
- `StartCommand.java` — 删除 --conn-req-algorithm CLI 选项
- `SimulationEngine.java` — 删除 getConnReqAlgorithm() 调用
- `ConnReqAuthHandlerTest.java` — 全部重写为明文比对场景
- `XmppStreamDecoderTest.java` — connReq 相关测试更新元素名和结构
- `IqHandlerTest.java` — 更新 connReq 测试
- `XmppConnectionTest.java` — ConnReqAuthConfig 构造参数变化
- `XmppStateBridgeTest.java` — ConnReqAuthConfig 构造参数变化