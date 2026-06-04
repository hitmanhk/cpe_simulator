## 1. 配置层

- [x] 1.1 在 `AppConfig.java` 新增 `localAddress` 字段（`@JsonProperty("local-address")`，String 类型，默认 null）及 getter/setter
- [x] 1.2 在 `application.yaml` 新增 `local-address: ""` 配置项

## 2. XMPP 连接绑定

- [x] 2.1 在 `XmppConnection.java` 的 `connect()` 方法中，读取 `AppConfig.localAddress`，非空时解析为 `InetAddress` 并调用 `bootstrap.localAddress(localAddr, 0)`；空时保持现有行为不调用
- [x] 2.2 `XmppConnection` 需能获取到 `AppConfig`（当前只持有 `XmppConfig`），需传入或通过其他方式获取顶层 `localAddress`

## 3. CWMP HTTP 连接绑定

- [x] 3.1 在 `CwmpClient.java` 构造方法中，读取 `AppConfig.localAddress`，非空时解析为 `InetAddress` 并在 `HttpClient.newBuilder()` 链中调用 `.localAddress(localAddr)`；空时保持现有行为不调用
- [x] 3.2 确保 `CwmpClient` 构造时已有 `AppConfig`（当前已传入 `AppConfig config`，直接使用即可）

## 4. 验证

- [x] 4.1 运行 `mvn test` 确认现有测试通过
- [x] 4.2 运行 `mvn clean package` 确认构建成功