## 1. YAML 数据模型

- [x] 1.1 在 tr181-default.yaml 中添加 `Device.XMPP.Connection.1.Password` 参数节点（type: xsd:string, value: "", writable: true, notification: 2）
- [x] 1.2 在 tr098-default.yaml 中添加 `InternetGatewayDevice.XMPP.Connection.1.Password` 参数节点（同上）

## 2. XmppConnection 密码字段

- [x] 2.1 XmppConnection.java 新增 `volatile String xmppPassword` 字段 + `getXmppPassword()` / `setXmppPassword(String)` 方法
- [x] 2.2 SaslHandler.java 修改 startAuth()：password 优先从 `connection.getXmppPassword()` 读取，null 时 fallback `config.getPassword()`

## 3. XmppEngine 新增方法

- [x] 3.1 XmppEngine.java 新增 `addConnection(XmppConnection conn)` 方法：将 conn 加入 connections 列表并调用 `conn.connect(eventLoopGroup)`

## 4. DeviceContext 接口与 CpeDevice 实现

- [x] 4.1 DeviceContext.java 接口新增 `void connectXmpp()` 方法
- [x] 4.2 CpeDevice.java 新增 `XmppEngine xmppEngine` 字段 + setter
- [x] 4.3 CpeDevice.java 实现 `connectXmpp()`：从 DataModel 读 XMPP Password，非空且 xmppConnection==null 时创建 XmppConnection、注入密码、注册状态回调、调用 xmppEngine.addConnection()
- [x] 4.4 定义 XMPP 参数路径常量（TR-181: `Device.XMPP.Connection.1.Password`，TR-098: `InternetGatewayDevice.XMPP.Connection.1.Password`）用于路径检测

## 5. SetParameterValuesHandler 检测逻辑

- [x] 5.1 在写入循环后新增检测：遍历参数列表，若包含 XMPP Password 路径且值非空且 `device.getXmppConnection() == null`，调用 `device.connectXmpp()`

## 6. SimulationEngine 启动流程调整

- [x] 6.1 NAT 非 xmpp-only 模式：只创建 XmppEngine + `start(List.of())`，不创建 XmppConnection，不注册 XMPP 状态回调
- [x] 6.2 将 xmppEngine 通过 setter 注入到每个 CpeDevice
- [x] 6.3 确保 xmpp-only 模式启动逻辑不受影响（仍批量创建 XmppConnection）

## 7. 测试验证

- [x] 7.1 验证 YAML 模型加载后 DataModel 包含 XMPP Password 参数节点
- [x] 7.2 验证 NAT 非 xmpp-only 模式启动时无 XmppConnection 创建
- [x] 7.3 验证 SetParameterValues 下发 XMPP Password 后 XmppConnection 被创建并使用正确密码
- [x] 7.4 验证 xmpp-only 模式仍使用全局密码正常连接
- [x] 7.5 验证 xmppPassword=null 时 SaslHandler fallback 到全局密码