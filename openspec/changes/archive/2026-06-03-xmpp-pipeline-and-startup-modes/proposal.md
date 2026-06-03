## Why

XMPP 子系统当前处于"脚手架"状态——所有 Handler 类（StreamHandler、SaslHandler、BindHandler、PresenceHandler、IqHandler、XmppStanzaDispatcher）均已实现，但从未被组装到 Netty pipeline 中。XmppConnection 仅建立 TCP 连接，无法完成 SASL 认证、资源绑定和 stanza 分发。同时，connectionRequestCallback 在 SimulationEngine 中创建后未向下传递到 IqHandler。此外，启动流程将 BOOT inform 和周期上报硬编码为必选步骤，缺少仅连接 XMPP 或仅发送一次性 inform 的灵活启动模式。

## What Changes

- **修复 XMPP Pipeline 组装**：XmppConnection.connect() 中创建所有 Handler 并注入 XmppStanzaDispatcher 到 Netty pipeline，使 XMPP 握手（Stream → SASL → Bind → Presence）完整运行
- **修复 connectionRequestCallback 传递链**：SimulationEngine → XmppEngine → XmppConnection → IqHandler，使 ACS 通过 XMPP IQ 发起的连接请求能正确触发 CWMP Inform
- **新增 XMPP-CpeDevice 状态桥接**：XmppConnection 到达 ONLINE 状态时，自动将对应 CpeDevice 状态设为 ONLINE
- **新增 `--xmpp-only` 启动模式**：仅执行 XMPP 连接，不启动 CwmpServer、不发送 BOOT inform、不启动周期上报，设备被动等待 ACS 通过 XMPP 发起连接请求
- **新增 `--no-periodic` 启动选项**：发送一次性 BOOT inform 后不启动周期上报，后续 CWMP 会话由 ACS 主动触发或手动通过 `device inform` 命令触发

## Capabilities

### New Capabilities
- `xmpp-pipeline-assembly`：XMPP Netty pipeline 的 Handler 创建、引用注入和 Dispatcher 注册，使 XMPP 连接能完成完整的握手和 stanza 处理流程
- `xmpp-device-state-bridge`：XmppConnection 与 CpeDevice 之间的状态同步机制，XMPP 上线后自动更新设备状态
- `startup-mode-control`：SimulationEngine 启动阶段拆分与 CLI 选项（`--xmpp-only`、`--no-periodic`），控制启动流程执行到哪个阶段

### Modified Capabilities
（无已有 spec 需要修改）

## Impact

- **xmpp 包**：XmppConnection（pipeline 组装）、XmppChannelInitializer（可能移除或重构）、XmppEngine（传递 callback）
- **engine 包**：SimulationEngine（启动阶段拆分、状态桥接注册）、CpeDevice（状态桥接回调）
- **cli 包**：StartCommand（新增 `--xmpp-only`、`--no-periodic` 选项）
- **common 包**：AppConfig（新增配置项）
- 无外部 API 变更，无依赖变更
