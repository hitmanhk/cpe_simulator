## ADDED Requirements

### Requirement: --xmpp-only 启动模式
系统 SHALL 支持 `--xmpp-only` CLI 选项，启动时仅执行 XMPP 连接，跳过 CwmpServer、BatchConnector、BOOT inform 和周期上报。

#### Scenario: xmpp-only 模式启动
- **WHEN** 使用 `--mode nat --xmpp-only` 启动
- **THEN** 系统加载模型、创建设备、启动 XmppEngine，但不启动 CwmpServer、不执行 BatchConnector、不发送 BOOT inform、不启动周期上报

#### Scenario: xmpp-only 模式下设备被动等待
- **WHEN** 设备通过 XMPP 上线后
- **THEN** 设备保持 ONLINE 状态，被动等待 ACS 通过 XMPP IQ 发起连接请求

#### Scenario: xmpp-only 模式下 ACS 连接请求触发 inform
- **WHEN** ACS 通过 XMPP 发送 connection request IQ
- **THEN** 设备触发 "6 CONNECTION REQUEST" inform 并进入正常 CWMP 会话

#### Scenario: xmpp-only 模式仅限 NAT 模式
- **WHEN** 使用 `--mode public --xmpp-only` 启动
- **THEN** 系统报错退出，提示 `--xmpp-only` 仅在 NAT 模式下可用

### Requirement: --no-periodic 启动选项
系统 SHALL 支持 `--no-periodic` CLI 选项，启动时发送 BOOT inform 但不启动周期上报。

#### Scenario: no-periodic 模式启动
- **WHEN** 使用 `--no-periodic` 启动
- **THEN** 系统正常启动（含 CwmpServer、BatchConnector、BOOT inform），但不启动周期上报

#### Scenario: no-periodic 模式下手动触发 inform
- **WHEN** 系统以 `--no-periodic` 模式运行
- **THEN** 用户可以通过 `device inform <id>` 命令手动触发单次 inform

#### Scenario: no-periodic 在 public 和 nat 模式下均可用
- **WHEN** 使用 `--mode public --no-periodic` 或 `--mode nat --no-periodic` 启动
- **THEN** 系统正常启动，行为符合对应模式但无周期上报

### Requirement: xmpp-only 隐含 no-periodic
当使用 `--xmpp-only` 时，系统 SHALL 自动禁用周期上报，无需额外指定 `--no-periodic`。

#### Scenario: xmpp-only 自动禁用周期上报
- **WHEN** 使用 `--xmpp-only` 启动
- **THEN** 周期上报不启动，等同于同时指定了 `--no-periodic`

### Requirement: xmpp-only 模式下 UDS 服务正常
`--xmpp-only` 模式下 UDS 管理接口 SHALL 正常启动，支持 status、device list、device inform 等命令。

#### Scenario: xmpp-only 模式下查询状态
- **WHEN** 系统以 `--xmpp-only` 模式运行，用户执行 `cpe-sim status`
- **THEN** 返回正确的设备数量和 XMPP 在线状态

#### Scenario: xmpp-only 模式下手动 inform
- **WHEN** 系统以 `--xmpp-only` 模式运行且设备已上线，用户执行 `cpe-sim device inform <id>`
- **THEN** 设备发送一次 inform 并返回成功
