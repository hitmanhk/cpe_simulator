## ADDED Requirements

### Requirement: XMPP Stream 建立与协商
系统 SHALL 实现 XMPP XML Stream 的建立（发送 `<stream:stream>` 开标签）和协商（处理服务器的 `<stream:features>`），支持流的正确关闭。

#### Scenario: 建立 XMPP Stream
- **WHEN** CPE 实例启动 XMPP 连接
- **THEN** 系统建立 TCP 连接，发送 XML Stream 开标签（含 `xmlns="jabber:client"`、`to` 属性），接收并解析服务器的 Stream 开标签和 `<stream:features>`

#### Scenario: 关闭 XMPP Stream
- **WHEN** CPE 实例需要断开 XMPP 连接
- **THEN** 系统发送 `</stream:stream>` 关闭标签，等待服务器关闭确认后断开 TCP 连接

### Requirement: XML Stream 编解码
系统 SHALL 实现基于 Netty 的 XML Stream 编解码器，正确处理 XMPP 的 XML 流式数据（无长度前缀、stanza 边界识别）。

#### Scenario: 解码完整 stanza
- **WHEN** 接收到完整的 XML stanza（如 `<iq>...</iq>`、`<presence/>`）
- **THEN** 系统将字节流解码为结构化的 Stanza 对象，传递给上层处理器

#### Scenario: 处理分片数据
- **WHEN** 一个 stanza 的数据分布在多个 TCP 包中
- **THEN** 系统缓冲数据，等待完整 stanza 后再解码，不产生半解析结果

#### Scenario: 处理特殊 XML 结构
- **WHEN** XML 中包含 CDATA、自关闭标签（`<presence/>`）、命名空间前缀
- **THEN** 系统正确解析所有 XML 边界情况，不产生解析错误

### Requirement: SASL PLAIN 认证
系统 SHALL 实现 SASL PLAIN 认证机制，使用配置的用户名和密码进行 XMPP 认证。

#### Scenario: PLAIN 认证成功
- **WHEN** 服务器 `<stream:features>` 中包含 `<mechanisms>` 且支持 PLAIN
- **THEN** 系统发送 `<auth mechanism="PLAIN">` （Base64 编码的 `\0username\0password`），收到 `<success/>` 后重新开启新 Stream

#### Scenario: PLAIN 认证失败
- **WHEN** 服务器返回 `<failure>`
- **THEN** 系统记录认证失败日志，标记连接为失败状态，不重试（密码错误无意义）

### Requirement: SASL SCRAM-SHA-1 认证
系统 SHALL 实现 SASL SCRAM-SHA-1 认证机制，支持多轮挑战-响应认证流程。

#### Scenario: SCRAM-SHA-1 认证成功
- **WHEN** 服务器支持 SCRAM-SHA-1 且 PLAIN 不可用
- **THEN** 系统完成 SCRAM-SHA-1 多轮认证（client-first → server-first → client-final → server-final），认证成功后重新开启新 Stream

### Requirement: Resource Binding
系统 SHALL 在认证成功后执行 XMPP Resource Binding，将 JID 与资源名绑定。

#### Scenario: 资源绑定
- **WHEN** 认证后的新 Stream 的 `<stream:features>` 包含 `<bind/>`
- **THEN** 系统发送 `<iq type="set"><bind><resource>sim</resource></bind></iq>`，接收绑定结果（完整 JID）

### Requirement: Presence 管理
系统 SHALL 在资源绑定后发送 Presence stanza，通知 XMPP 服务器设备上线状态。

#### Scenario: 发送上线 Presence
- **WHEN** Resource Binding 成功完成
- **THEN** 系统发送 `<presence/>` stanza，标记连接为在线状态

#### Scenario: 发送离线 Presence
- **WHEN** CPE 实例需要下线
- **THEN** 系统发送 `<presence type="unavailable"/>`，然后关闭 Stream

### Requirement: IQ Stanza 处理（Connection Request）
系统 SHALL 处理来自 ACS（通过 XMPP Server 转发）的 IQ stanza，识别 TR-069 Connection Request 并触发 CWMP Inform。

#### Scenario: 收到 Connection Request IQ
- **WHEN** 收到 `<iq type="set">` 包含 `<ConnectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq"/>`
- **THEN** 系统立即回复 `<iq type="result"/>`，通知对应 CPE 实例触发 EventCode 为 "6 CONNECTION REQUEST" 的 Inform

#### Scenario: 收到未知 IQ
- **WHEN** 收到未识别的 `<iq type="get">` 或 `<iq type="set">`
- **THEN** 系统回复 `<iq type="error"><error type="cancel"><service-unavailable/></error></iq>`

### Requirement: XMPP Ping/Pong Keepalive（XEP-0199）
系统 SHALL 实现 XEP-0199 Ping/Pong 机制，保持 XMPP 连接活跃并检测连接断开。

#### Scenario: 响应服务器 Ping
- **WHEN** 收到 `<iq type="get"><ping xmlns="urn:xmpp:ping"/></iq>`
- **THEN** 系统回复 `<iq type="result"/>`

#### Scenario: 主动发送 Ping
- **WHEN** 连接空闲超过配置的 keepalive 间隔（默认 60 秒）
- **THEN** 系统向服务器发送 Ping，如果在超时时间内（默认 30 秒）未收到 Pong，判定连接断开并触发重连

### Requirement: XMPP 断线重连
系统 SHALL 在 XMPP 连接断开后自动重连，使用指数退避策略避免重连风暴。

#### Scenario: 连接意外断开
- **WHEN** TCP 连接意外断开（网络故障、服务器重启等）
- **THEN** 系统按指数退避策略重连（初始 1s，最大 60s），重连成功后重新执行 Stream 协商 → SASL 认证 → Bind → Presence 完整流程

#### Scenario: 重连风暴控制
- **WHEN** 大量连接同时断开（如 XMPP 服务器重启）
- **THEN** 系统对重连请求进行随机延迟分散，避免同时重连导致服务器过载

### Requirement: XMPP 连接批量建立
系统 SHALL 支持批量建立 XMPP 连接，通过速率控制避免对 XMPP 服务器造成过大压力。

#### Scenario: 批量建立 50K 连接
- **WHEN** 模拟器启动 50,000 个 CPE 实例的 XMPP 连接
- **THEN** 系统按批次建立连接（每批 1000 个，批次间隔 200ms），总耗时约 10-15 秒完成全部连接

### Requirement: Netty EventLoop 与虚拟线程协作
系统 SHALL 使用 Netty EventLoop 处理 IO 和 XML 解码，将业务逻辑（SASL 认证、IQ 处理、CWMP 触发）委托给虚拟线程执行。

#### Scenario: IO 与业务分离
- **WHEN** Netty EventLoop 接收到完整的 XMPP stanza
- **THEN** EventLoop 完成 XML 解码后，将 stanza 提交到虚拟线程执行器处理，EventLoop 不阻塞继续处理其他连接的 IO

#### Scenario: 虚拟线程阻塞不影响 IO
- **WHEN** 虚拟线程中执行阻塞操作（如触发 CWMP Inform 需要 HTTP 请求）
- **THEN** 虚拟线程挂起等待，Netty EventLoop 不受影响，继续处理其他连接的 IO 事件
