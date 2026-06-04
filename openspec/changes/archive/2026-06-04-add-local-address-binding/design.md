## Context

CPE 模拟器运行在多网卡执行机上时，所有出站 TCP 连接（XMPP 连接到 XMPP 服务器、CWMP HTTP Inform 连接到 ACS）均由操作系统路由选择出口 IP。当前代码中：
- XMPP 使用 Netty Bootstrap（`XmppConnection.java`），无 `.localAddress()` 调用
- CWMP 使用 JDK `HttpClient`（`CwmpClient.java`），无 `.localAddress()` 调用

JDK 19+ 的 `HttpClient.Builder` 已支持 `.localAddress(InetAddress)`，Netty Bootstrap 同样支持 `.localAddress(InetAddress, int)`，因此无需更换 HTTP 客户端实现。

## Goals / Non-Goals

**Goals:**
- 通过全局 YAML 配置 `local-address` 指定出站连接的源 IP
- XMPP 和 CWMP HTTP 连接均绑定到该 IP（非空时生效）
- 空值或 null 时保持现有行为（OS 自动选择）

**Non-Goals:**
- 每设备级别的本地地址分配（全局单 IP）
- 多 IP 轮询或按比例分散
- CWMP 服务器（`CwmpServer`，接收 Connection Request 的监听端）的绑定地址变更（已有 `conn-req-host` 配置）

## Decisions

### D1: 配置放在顶层 AppConfig，而非 XmppConfig

**选择**: 在 `AppConfig` 顶层加 `localAddress` 字段

**理由**: 本地绑定地址同时影响 XMPP 和 CWMP HTTP，放在 XMPP 子配置中语义不对。顶层配置表明"全局生效"。

**替代方案**: 分别在 `XmppConfig` 和 CWMP 相关配置中各加一个字段 → 重复配置，用户需要设两次同一个 IP。

### D2: 使用 InetAddress 类型，YAML 键为 local-address

**选择**: `String` 类型存储，运行时解析为 `InetAddress`

**理由**: YAML 中直接写 IP 字符串（如 `10.0.2.5`）最自然。空字符串或 null 表示不绑定。代码中在需要时解析为 `InetAddress` 传入 API。

### D3: XMPP 侧使用 bootstrap.localAddress(addr, 0)

**选择**: Netty Bootstrap `.localAddress(localAddr, 0)`，端口为 0（随机分配）

**理由**: 只需控制源 IP，不需要固定源端口。这与 JDK HttpClient 的 `.localAddress()` 行为一致（后者只绑 IP，端口自动分配）。

### D4: CWMP 侧使用 HttpClient.Builder.localAddress(addr)

**选择**: 直接在 `HttpClient.newBuilder()` 链中加 `.localAddress(localAddr)`

**理由**: JDK 19+ 已原生支持，一行代码解决。无需换 Netty HTTP 客户端。

## Risks / Trade-offs

- [指定的 IP 不可达目标] → 启动时不做预校验，连接失败时错误信息中应体现本地绑定地址，便于排查
- [IPv6 地址兼容性] → `InetAddress` 同时支持 IPv4/IPv6，YAML 中填 IPv6 地址字符串也能正常解析
- [null vs 空字符串语义] → 两者均表示"不绑定"，代码中统一处理：`null` 或空字符串 → 不调用 `.localAddress()`