## ADDED Requirements

### Requirement: 全局本地地址配置
系统 SHALL 在 `AppConfig` 顶层提供 `local-address` 配置字段，类型为字符串，默认值为空。当值为空或 null 时，系统 SHALL 使用操作系统自动选择的出口 IP。当值为有效 IP 地址字符串时，系统 SHALL 将所有出站 TCP 连接绑定到该 IP 地址。

#### Scenario: 配置为空时的行为
- **WHEN** `local-address` 配置为空字符串或 null
- **THEN** XMPP 连接和 CWMP HTTP 连接均不绑定本地地址，由操作系统自动选择出口接口

#### Scenario: 配置为有效 IPv4 地址时的行为
- **WHEN** `local-address` 配置为 `"10.0.2.5"`
- **THEN** XMPP 连接的 Netty Bootstrap 绑定 `10.0.2.5:0`，CWMP HTTP 连接的 HttpClient 绑定 `10.0.2.5`

#### Scenario: 配置为无效 IP 地址时的行为
- **WHEN** `local-address` 配置为 `"not-an-ip"`
- **THEN** 系统在解析 IP 时 SHALL 抛出异常，阻止启动

### Requirement: XMPP 连接绑定本地地址
当 `local-address` 配置非空时，`XmppConnection` SHALL 在 Netty Bootstrap 构建中调用 `.localAddress(InetAddress, 0)`，使所有 XMPP TCP 连接从指定本地地址发出。源端口 SHALL 为 0（操作系统随机分配）。

#### Scenario: XMPP 连接使用指定本地地址
- **WHEN** `local-address` 配置为 `"10.0.2.5"` 且 XMPP 连接发起 TCP 连接
- **THEN** TCP 连接的源 IP 为 `10.0.2.5`，源端口由操作系统随机分配

#### Scenario: XMPP 连接未指定本地地址
- **WHEN** `local-address` 配置为空
- **THEN** XMPP 连接的 Netty Bootstrap 不调用 `.localAddress()`，TCP 连接的源 IP 由操作系统路由决定

### Requirement: CWMP HTTP 连接绑定本地地址
当 `local-address` 配置非空时，`CwmpClient` SHALL 在 `HttpClient.newBuilder()` 中调用 `.localAddress(InetAddress)`，使所有 CWMP HTTP 请求从指定本地地址发出。

#### Scenario: CWMP HTTP 请求使用指定本地地址
- **WHEN** `local-address` 配置为 `"10.0.2.5"` 且 CPE 设备向 ACS 发送 Inform
- **THEN** HTTP 请求的 TCP 连接源 IP 为 `10.0.2.5`

#### Scenario: CWMP HTTP 请求未指定本地地址
- **WHEN** `local-address` 配置为空
- **THEN** `HttpClient.Builder` 不调用 `.localAddress()`，HTTP 请求的源 IP 由操作系统路由决定