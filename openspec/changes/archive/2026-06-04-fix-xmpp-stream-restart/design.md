## Context

当前 XMPP 客户端实现中，`XmppStreamDecoder` 使用 `streamOpened` 标志来跟踪是否已解析过 `<stream:stream>` 开标签。这个标志在初始流打开时设为 `true`，但 XMPP 协议在 SASL 认证成功（以及 TLS 协商成功）后要求**流重启**——客户端和服务器都要重新发送 `<stream:stream>` + `<stream:features>`。

当前代码中，`XmppStreamDecoder.reset()` 方法已存在（清空 accumulator 并重置 `streamOpened=false`），但**没有任何代码调用它**。这导致流重启后服务器发来的第二个 `<stream:stream>` 被忽略，后续所有 stanza 无法解析，设备永远卡在 AUTHENTICATED 状态。

现有代码结构：
- `XmppConnection.connect()` 在 Bootstrap 的 `ChannelInitializer` 中创建 `XmppStreamDecoder` 实例并加入 pipeline
- `SaslHandler.handleAuthResult()` 在收到 `<success>` 后发送新的 `<stream:stream>`，但没有触发 decoder 重置
- decoder 实例在 pipeline 中，需要通过 Netty Channel pipeline 才能访问

## Goals / Non-Goals

**Goals:**
- SASL 认证成功后流重启时，decoder 正确重置状态，使后续 `<stream:stream>` 和 `<stream:features>` 能正常解析
- Resource Bind 流程能顺利完成，设备能到达 ONLINE 状态
- 为未来 TLS StartTLS 协商后的流重启也预留同样的重置机制

**Non-Goals:**
- 不重构 XMPP 客户端的整体架构（仍使用手写协议栈）
- 不引入第三方 XMPP 库
- 不修改 `XmppStreamDecoder` 的核心解析逻辑（只修复流重启标志问题）

## Decisions

### 决策 1：通过 Channel Pipeline 访问 decoder 并调用 reset()

**选择**：在 `SaslHandler.handleAuthResult()` 中，通过 `connection.getChannel().pipeline().get(XmppStreamDecoder.class)` 获取 decoder 实例并调用 `reset()`

**备选方案**：
- A) 在 `XmppConnection` 中缓存 decoder 引用：需要在 `ChannelInitializer` 中将 decoder 实例传出来，增加耦合
- B) 发送一个自定义 Netty 事件沿 pipeline 传播：过度工程化，此场景只需简单的方法调用

**理由**：方案最简单直接，利用 Netty pipeline 已有的命名 handler 查找机制（`pipeline.get("xmppDecoder")`），不增加额外数据结构。Netty pipeline 的 `get()` 方法本身就是官方推荐的跨 handler 通信方式之一。

### 决策 2：在发送新 StreamOpen 之前重置 decoder

**选择**：先 `reset()`，再 `sendStanza(StreamOpen)`，顺序严格保证

**理由**：如果先发 StreamOpen 再 reset，存在时序风险——服务器响应可能在 reset 之前到达。先 reset 确保 decoder 处于干净状态后再发起流重启。

### 决策 3：清空 accumulator 与否

**选择**：保留 `reset()` 方法中已有的 `accumulator.setLength(0)` 行为

**理由**：流重启时旧流中可能残留未消费的数据片段，清空 accumulator 是安全的做法。这些残留数据属于旧流，不应被新流解析。

## Risks / Trade-offs

- **[风险] pipeline.get() 在 TLS 场景下可能返回 null** → Mitigation：如果 decoder 为 null，跳过 reset 并记录警告日志，不中断流程。实际上 decoder 始终存在于 pipeline 中（在 SSL handler 之后），所以这个风险很低
- **[风险] reset() 清空 accumulator 可能丢失尚未解析的 stanza** → Mitigation：流重启发生在 `<success>` 确认之后，此时旧流已完成，不应有未处理的 stanza。如果有，它们属于旧流的尾部噪声，丢弃是正确行为
- **[权衡] 使用 pipeline.get() 增加了 SaslHandler 对 Netty pipeline 的间接依赖** → 这是当前架构（pipeline handler 间通过 connection 间接通信）的自然延伸，不引入新耦合模式