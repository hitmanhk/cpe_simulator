## 1. XMPP Pipeline 组装

- [x] 1.1 XmppConnection 构造函数增加 `Consumer<String> connectionRequestCallback` 参数
- [x] 1.2 XmppConnection.connect() 中创建 Handler 实例集（StreamHandler、SaslHandler、BindHandler、PresenceHandler、PingHandler、IqHandler），注入互相引用
- [x] 1.3 XmppConnection.connect() 中创建 XmppStanzaDispatcher 并添加到 Netty pipeline（Decoder → Encoder → Dispatcher）
- [x] 1.4 TCP 连接成功后自动调用 StreamHandler.openStream() 发起 XMPP 握手
- [x] 1.5 移除或简化 XmppChannelInitializer，pipeline 组装逻辑移入 XmppConnection

## 2. connectionRequestCallback 传递链

- [x] 2.1 XmppEngine 在创建 XmppConnection 时传入 connectionRequestCallback
- [x] 2.2 XmppConnection 将 connectionRequestCallback 传入 IqHandler 构造函数
- [x] 2.3 验证 IqHandler.handleConnectionRequest() 能正确触发回调

## 3. XMPP-CpeDevice 状态桥接

- [x] 3.1 XmppConnection 增加 `onStateChange` 回调注册方法（`Consumer<ConnectionState>`）
- [x] 3.2 XmppConnection.setState() 中触发回调
- [x] 3.3 SimulationEngine 创建 XmppConnection 时注册回调：ONLINE → CpeDevice.ONLINE，FAILED → CpeDevice.ERROR，DISCONNECTED → CpeDevice.OFFLINE

## 4. 配置项与 CLI 选项

- [x] 4.1 AppConfig 新增 `xmppOnly`（boolean）和 `noPeriodic`（boolean）字段及 getter/setter
- [x] 4.2 StartCommand 新增 `--xmpp-only` 和 `--no-periodic` CLI 选项，映射到 AppConfig
- [x] 4.3 StartCommand.validateNatMode() 增加校验：`--xmpp-only` 仅在 NAT 模式下可用
- [x] 4.4 ConfigLoader 支持从 YAML 配置文件加载 xmppOnly 和 noPeriodic

## 5. SimulationEngine 启动阶段拆分

- [x] 5.1 SimulationEngine.start() 中 CwmpServer 启动逻辑增加 `!config.isXmppOnly()` 条件
- [x] 5.2 SimulationEngine.start() 中 BatchConnector 执行增加 `!config.isXmppOnly()` 条件
- [x] 5.3 SimulationEngine.start() 中 BOOT inform 触发增加 `!config.isXmppOnly()` 条件
- [x] 5.4 SimulationEngine.start() 中周期上报调度增加 `!config.isXmppOnly() && !config.isNoPeriodic()` 条件
- [x] 5.5 xmpp-only 模式下设备初始状态保持 CREATED，等待 XMPP 回调设为 ONLINE

## 6. 测试验证

- [x] 6.1 编写 XmppConnection pipeline 组装单元测试：验证 Handler 创建和 Dispatcher 注册
- [x] 6.2 编写状态桥接单元测试：验证 XmppConnection 状态变更触发 CpeDevice 状态更新
- [x] 6.3 编写启动模式集成测试：验证 xmpp-only 模式跳过 CwmpServer/BatchConnector/BOOT/Periodic
- [x] 6.4 编写启动模式集成测试：验证 no-periodic 模式跳过周期上报但执行 BOOT inform
- [x] 6.5 运行 `mvn test` 确认所有测试通过
