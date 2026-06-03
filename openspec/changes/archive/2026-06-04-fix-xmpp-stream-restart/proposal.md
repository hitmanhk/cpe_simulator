## Why

XMPP NAT 模式的 SASL 认证成功后，模拟器无法完成 Resource Bind，导致设备永远无法上线。根因是 `XmppStreamDecoder` 的 `streamOpened` 标志在流重启（stream restart）时未重置，导致服务器发来的第二个 `<stream:stream>` 标签被忽略，后续的 `<stream:features>` 也无法解析，Bind 请求永远不会发出。

## What Changes

- 修复 `XmppStreamDecoder`：在 SASL 成功后的流重启场景中重置 `streamOpened` 标志
- 在 `XmppConnection` 中暴露 pipeline decoder 访问能力，以便在流重启时调用 decoder 的 `reset()` 方法
- 确保 `SaslHandler` 在发送第二个 `<stream:stream>` 之前触发 decoder 重置

## Capabilities

### New Capabilities

- `xmpp-stream-restart`: 确保 XMPP 流重启（SASL 后、TLS 后）时解码器状态正确重置，使后续 stanza 能正常解析

### Modified Capabilities

（无现有 spec 需修改）

## Impact

- `XmppStreamDecoder.java`：解码逻辑变更，需重置 `streamOpened` 标志
- `XmppConnection.java`：新增 decoder 访问方法
- `SaslHandler.java`：在流重启时调用 decoder 重置
- `XmppChannelInitializer.java`：可能需要调整 pipeline 构建以暴露 decoder 实例
- 所有 XMPP NAT 模式的设备连接行为将修正