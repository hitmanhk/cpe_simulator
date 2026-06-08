## Why

在 NAT 非 xmpp-only 场景下，CWMP 和 XMPP 同时连接，但 XMPP 的账户和密码当前是固定的全局配置值（所有设备共享同一密码）。ACS 无法通过 TR-069 为每个设备单独下发 XMPP 凭证。需要支持 ACS 通过 SetParameterValues 下发 XMPP 密码后，设备才建立 XMPP 连接。

## What Changes

- 在 TR-181/TR-098 YAML 数据模型中新增 `XMPP.Connection.1.Password` 参数节点（writable=true, notification=2）
- NAT 非 xmpp-only 模式启动时不再批量创建 XMPP 连接，等待 ACS 下发密码后才建立连接
- XmppConnection 新增 `xmppPassword` 字段，SaslHandler 从该字段读取认证密码
- SetParameterValuesHandler 检测到 XMPP Password 参数被设置且非空时，触发 XMPP 连接创建
- XmppEngine 新增 `addConnection()` 方法，支持为单个设备后加连接
- xmppPassword 为 null 时 SaslHandler fallback 到全局配置密码，保持 xmpp-only 等旧模式兼容

## Capabilities

### New Capabilities
- `xmpp-credential`: ACS 通过 TR-069 SetParameterValues 下发 XMPP 密码后设备动态建立 XMPP 连接

### Modified Capabilities

## Impact

- YAML 模型文件（tr181-default.yaml、tr098-default.yaml）
- XmppConnection、SaslHandler（XMPP 认证密码来源变更）
- SetParameterValuesHandler（新增 XMPP 参数检测逻辑）
- CpeDevice（新增 xmppEngine 字段和 connectXmpp 方法）
- DeviceContext 接口（新增 connectXmpp 方法）
- SimulationEngine（NAT 模式启动流程变更）
- XmppEngine（新增 addConnection 方法）