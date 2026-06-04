## Why

执行机多网卡环境下，XMPP 连接和 CWMP HTTP 连接均从操作系统默认接口发出，无法指定源 IP。测试场景需要模拟来自特定网络的 CPE 设备，必须控制出站连接的本地绑定地址。

## What Changes

- 在 `AppConfig` 顶层新增 `localAddress` 配置字段（`local-address` YAML 键），默认值为空（OS 自动选择）
- XMPP 连接：`XmppConnection.java` Netty Bootstrap 在 `localAddress` 非空时调用 `.localAddress(localAddr, 0)` 绑定指定 IP
- CWMP HTTP 连接：`CwmpClient.java` JDK `HttpClient.Builder` 在 `localAddress` 非空时调用 `.localAddress(localAddr)` 绑定指定 IP
- `application.yaml` 新增 `local-address` 配置项

## Capabilities

### New Capabilities
- `local-address-binding`: 全局配置本地绑定地址，使所有出站 TCP 连接（XMPP 和 CWMP HTTP）从指定 IP 发出

### Modified Capabilities
<!-- 无已有 spec 需修改 -->

## Impact

- `AppConfig.java` — 新增字段 + getter/setter
- `XmppConnection.java` — Bootstrap 构建逻辑增加条件性 `.localAddress()` 调用
- `CwmpClient.java` — HttpClient.Builder 构建逻辑增加条件性 `.localAddress()` 调用
- `application.yaml` — 新增配置项
- 无 API 变更，无依赖变更，无破坏性改动