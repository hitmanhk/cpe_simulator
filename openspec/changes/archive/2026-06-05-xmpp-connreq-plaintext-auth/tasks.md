## 1. 配置层清理

- [x] 1.1 修改 `ConnReqAuthConfig.java`：删除 `algorithm` 字段，record 改为 `(boolean enabled, String username, String password)`
- [x] 1.2 修改 `AppConfig.java`：删除 `connReqAlgorithm` 字段、`@JsonProperty("conn-req-algorithm")`、getter/setter
- [x] 1.3 修改 `StartCommand.java`：删除 `--conn-req-algorithm` CLI 选项及 `setConnReqAlgorithm` 调用
- [x] 1.4 修改 `SimulationEngine.java`：删除 `config.getConnReqAlgorithm()` 调用，`ConnReqAuthConfig` 构造参数改为三个

## 2. ConnReqAuthHandler 核心逻辑重写

- [x] 2.1 重写 `handle()` 方法：一步明文比对逻辑（提取 username/password 子元素文本，与配置值直接比较）
- [x] 2.2 重写 `parseConnReqAttrs()` → `parseConnReqChildElements()`：从 rawXml 提取 `<username>xxx</username>` 和 `<password>xxx</password>` 子元素文本值
- [x] 2.3 重写 `sendAuthError()`：包含原始 `<connectionRequest>` 请求体内容 + `<error type="cancel">`
- [x] 2.4 删除 `sendChallenge()` 方法及所有 nonce 状态字段（`pendingNonce`、`pendingRealm`、`pendingAlgorithm`、`nonceTimestamp`、`NONCE_EXPIRY_MS`）
- [x] 2.5 删除 `generateNonce()`、`isNonceExpired()`、`clearNonce()`、`forceNonceExpiry()` 方法
- [x] 2.6 删除 `DigestAuth` import 及所有引用

## 3. 测试重写

- [x] 3.1 重写 `ConnReqAuthHandlerTest.java`：覆盖明文认证场景（认证禁用、凭证匹配、用户名不匹配、密码不匹配、不含子元素）
- [x] 3.2 更新 `XmppStreamDecoderTest.java`：connReq 相关测试改为 `<connectionRequest>` 元素名和子元素结构
- [x] 3.3 更新 `IqHandlerTest.java`：connReq IQ 测试改为明文认证场景
- [x] 3.4 更新 `XmppConnectionTest.java`：所有 `ConnReqAuthConfig` 构造参数改为三个（去掉 algorithm）
- [x] 3.5 更新 `XmppStateBridgeTest.java`：`ConnReqAuthConfig` 构造参数改为三个
- [x] 3.6 运行 `mvn test` 确认所有测试通过