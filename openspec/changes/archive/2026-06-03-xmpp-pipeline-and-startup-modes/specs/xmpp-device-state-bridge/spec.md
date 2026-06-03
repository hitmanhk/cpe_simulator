## ADDED Requirements

### Requirement: XmppConnection 状态变更通知
XmppConnection SHALL 支持注册状态变更回调（`Consumer<ConnectionState>`），在状态发生变化时触发。

#### Scenario: 注册回调后状态变更被通知
- **WHEN** 注册了 onStateChange 回调且连接状态从 BINDING 变为 BOUND
- **THEN** 回调被调用，参数为新状态 BOUND

#### Scenario: 未注册回调时不抛异常
- **WHEN** 未注册 onStateChange 回调且连接状态变化
- **THEN** 状态正常变更，无异常抛出

### Requirement: XMPP 上线自动同步设备状态
SimulationEngine SHALL 为每个 XmppConnection 注册状态回调，当 XmppConnection 状态变为 ONLINE 时，将对应 CpeDevice 状态设为 ONLINE。

#### Scenario: XMPP 连接上线后设备可用
- **WHEN** 某设备的 XmppConnection 完成握手并进入 ONLINE 状态
- **THEN** 对应 CpeDevice 的 DeviceState 变为 ONLINE，可以接受 triggerInform 调用

#### Scenario: XMPP 连接失败后设备状态更新
- **WHEN** 某设备的 XmppConnection 进入 FAILED 状态
- **THEN** 对应 CpeDevice 的 DeviceState 变为 ERROR

### Requirement: XMPP 断线后设备状态更新
当 XmppConnection 状态变为 DISCONNECTED 时，对应 CpeDevice 状态 SHALL 变为 OFFLINE。

#### Scenario: XMPP 断线后设备不可用
- **WHEN** XmppConnection 断开连接，状态变为 DISCONNECTED
- **THEN** 对应 CpeDevice 的 DeviceState 变为 OFFLINE
