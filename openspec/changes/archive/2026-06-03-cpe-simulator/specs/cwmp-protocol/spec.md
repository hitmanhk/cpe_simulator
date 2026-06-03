## ADDED Requirements

### Requirement: SOAP Envelope 构建与解析
系统 SHALL 能够构建符合 TR-069 规范的 SOAP XML 消息，包括 Envelope、Header（含 cwmp:ID）和 Body。系统 SHALL 能够解析 ACS 返回的 SOAP XML 消息，提取方法名、cwmp:ID 和参数。

#### Scenario: 构建 Inform SOAP 消息
- **WHEN** CPE 需要发送 Inform 请求
- **THEN** 系统生成包含 DeviceId、Event、ParameterList 等完整字段的 SOAP XML，格式符合 `urn:dslforum-org:cwmp-1-0` 命名空间规范

#### Scenario: 解析 ACS 的 GetParameterValues 请求
- **WHEN** 收到 ACS 下发的 GetParameterValues SOAP 消息
- **THEN** 系统提取 cwmp:ID、ParameterList 中的参数路径列表

#### Scenario: 解析 SOAP Fault
- **WHEN** 收到 ACS 返回的 SOAP Fault 消息
- **THEN** 系统提取 FaultCode 和 FaultString，记录日志并关闭当前会话

### Requirement: CWMP 会话状态机
系统 SHALL 实现 CWMP 会话状态机，包含 IDLE、INFORM_SENT、CPE_HOLD、ACS_HOLD 四个状态，按 TR-069 规范进行状态转换。

#### Scenario: 定时触发 Inform 会话
- **WHEN** 定时 Inform 间隔到达
- **THEN** 系统从 IDLE 状态转入 INFORM_SENT，发送 Inform SOAP 消息到 ACS

#### Scenario: ACS 持有会话阶段
- **WHEN** CPE 发送空 body（无更多 RPC）且 ACS 回复包含 SOAP body 的 200 OK
- **THEN** 系统进入 ACS_HOLD 状态，解析并处理 ACS 下发的 RPC 方法

#### Scenario: 会话正常结束
- **WHEN** ACS 回复 204 No Content（空 body）
- **THEN** 系统从 ACS_HOLD 状态转回 IDLE 状态，关闭当前会话

#### Scenario: 会话超时
- **WHEN** 任何状态下 30 秒无 HTTP 响应
- **THEN** 系统强制关闭会话，转入 IDLE 状态，记录超时错误

### Requirement: Inform RPC（CPE → ACS）
系统 SHALL 实现 Inform RPC 方法，发送设备标识、事件列表和参数值到 ACS。

#### Scenario: 发送 Periodic Inform
- **WHEN** 定时 Inform 间隔到达
- **THEN** 系统发送 EventCode 为 "2 PERIODIC" 的 Inform，包含 DeviceId（Manufacturer、OUI、ProductClass、SerialNumber）和配置的 ParameterList

#### Scenario: 发送 BOOT Inform
- **WHEN** 设备模拟重启完成
- **THEN** 系统发送 EventCode 为 "1 BOOT" 的 Inform

#### Scenario: 发送 CONNECTION REQUEST Inform
- **WHEN** 收到 ACS 反向回连请求
- **THEN** 系统发送 EventCode 为 "6 CONNECTION REQUEST" 的 Inform

#### Scenario: 发送 VALUE CHANGE Inform
- **WHEN** 被监控的参数值发生变化
- **THEN** 系统发送 EventCode 为 "4 VALUE CHANGE" 的 Inform

### Requirement: GetParameterValues RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetParameterValues 请求，从数据模型中读取指定参数的值并返回。

#### Scenario: 查询单个参数
- **WHEN** ACS 请求查询 `Device.DeviceInfo.Manufacturer`
- **THEN** 系统返回该参数的当前值，类型为 `xsd:string`

#### Scenario: 查询多个参数
- **WHEN** ACS 请求查询多个参数路径
- **THEN** 系统返回所有请求参数的名称、值和类型

#### Scenario: 查询不存在的参数
- **WHEN** ACS 请求查询不存在的参数路径
- **THEN** 系统返回 SOAP Fault，FaultCode 为 9005（Invalid parameter name）

### Requirement: SetParameterValues RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 SetParameterValues 请求，将指定参数值写入数据模型。

#### Scenario: 设置可写参数
- **WHEN** ACS 请求设置 `Device.ManagementServer.PeriodicInformInterval` 为 600
- **THEN** 系统更新参数值，返回 Status 为 0（成功）

#### Scenario: 设置只读参数
- **WHEN** ACS 请求设置只读参数（如 `Device.DeviceInfo.SerialNumber`）
- **THEN** 系统返回 SOAP Fault，FaultCode 为 9008（Attempt to set non-writable parameter）

#### Scenario: 设置无效值
- **WHEN** ACS 请求设置参数值类型不匹配（如 int 类型参数传入 string）
- **THEN** 系统返回 SOAP Fault，FaultCode 为 9007（Invalid parameter value）

### Requirement: GetParameterNames RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetParameterNames 请求，返回指定路径下的参数名列表。

#### Scenario: 查询根路径参数名
- **WHEN** ACS 请求查询 `Device.` 下的参数名，NextLevel 为 true
- **THEN** 系统返回 `Device.` 直接子节点的参数名列表

#### Scenario: 查询子树参数名
- **WHEN** ACS 请求查询 `Device.DeviceInfo.` 下的参数名，NextLevel 为 false
- **THEN** 系统返回该路径下所有层级参数的完整名称列表

### Requirement: GetParameterAttributes RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetParameterAttributes 请求，返回参数的通知属性和访问权限。

#### Scenario: 查询参数属性
- **WHEN** ACS 请求查询参数属性
- **THEN** 系统返回每个参数的 Notification 类型（0=关闭, 1=被动, 2=主动）和 AccessList

### Requirement: SetParameterAttributes RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 SetParameterAttributes 请求，修改参数的通知属性。

#### Scenario: 设置参数通知类型
- **WHEN** ACS 请求将某参数的 Notification 设置为 2（主动通知）
- **THEN** 系统更新参数通知属性，当该参数值变化时触发 VALUE CHANGE Inform

### Requirement: AddObject RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 AddObject 请求，在数据模型中创建新的多实例对象。

#### Scenario: 添加 WANConnectionDevice 实例
- **WHEN** ACS 请求在 `Device.WANDevice.1.WANConnectionDevice.` 下添加新实例
- **THEN** 系统创建新实例（如 `.2.`），返回 InstanceNumber 和 Status 为 0

### Requirement: DeleteObject RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 DeleteObject 请求，从数据模型中删除指定的多实例对象。

#### Scenario: 删除实例对象
- **WHEN** ACS 请求删除 `Device.WANDevice.1.WANConnectionDevice.2.`
- **THEN** 系统删除该实例及其所有子参数，返回 Status 为 0

### Requirement: Download RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 Download 请求，模拟固件升级或配置文件下载。

#### Scenario: 模拟固件升级
- **WHEN** ACS 下发 FileType 为 "1 Firmware Upgrade Image" 的 Download 请求
- **THEN** 系统立即返回 DownloadResponse（Status=0），延迟可配置时间后发送 TransferComplete（成功），并更新 SoftwareVersion 参数

#### Scenario: 模拟下载失败
- **WHEN** 配置的成功率未命中（如 1% 失败率）
- **THEN** 系统发送 TransferComplete，包含 FaultCode 表示下载失败

### Requirement: Reboot RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 Reboot 请求，模拟设备重启。

#### Scenario: 模拟重启
- **WHEN** ACS 下发 Reboot 请求
- **THEN** 系统返回 RebootResponse，关闭当前 CWMP 会话，延迟可配置时间后重新初始化（重置 uptime 等参数），发送 EventCode 为 "1 BOOT" 的 Inform，NAT 模式下重新建立 XMPP 连接

### Requirement: FactoryReset RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 FactoryReset 请求，模拟恢复出厂设置。

#### Scenario: 模拟恢复出厂
- **WHEN** ACS 下发 FactoryReset 请求
- **THEN** 系统返回 FactoryResetResponse，重新加载默认数据模型（丢弃所有运行时修改），关闭当前会话，延迟后发送 BOOT Inform

### Requirement: ScheduleInform RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 ScheduleInform 请求，在指定延迟后发送 Inform。

#### Scenario: 调度延迟 Inform
- **WHEN** ACS 下发 ScheduleInform，DelaySeconds 为 60
- **THEN** 系统返回 ScheduleInformResponse，60 秒后发送 EventCode 为 "M ScheduleInform" 的 Inform

### Requirement: GetQueuedTransfers RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetQueuedTransfers 请求，返回当前排队的传输任务列表。

#### Scenario: 查询排队传输
- **WHEN** ACS 请求查询排队的传输任务
- **THEN** 系统返回当前排队的 Download/Upload 任务列表（CommandKey、FileType、State）

### Requirement: GetAllQueuedTransfers RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetAllQueuedTransfers 请求，返回所有排队的传输任务。

#### Scenario: 查询所有排队传输
- **WHEN** ACS 请求查询所有排队传输
- **THEN** 系统返回所有排队的传输任务列表，包含完整的传输信息

### Requirement: GetRPCMethods RPC（CPE → ACS）
系统 SHALL 在 CPE 持有会话阶段响应 ACS 的 GetRPCMethods 请求，返回支持的 RPC 方法列表。

#### Scenario: 返回支持的 RPC 列表
- **WHEN** ACS 请求获取 CPE 支持的 RPC 方法
- **THEN** 系统返回所有已实现的 RPC 方法名称列表

### Requirement: TransferComplete RPC（CPE → ACS）
系统 SHALL 在 Download/Upload 完成后主动向 ACS 发送 TransferComplete 通知。

#### Scenario: Download 完成后通知
- **WHEN** 模拟 Download 完成（成功或失败）
- **THEN** 系统在下一个 CPE 持有会话阶段发送 TransferComplete，包含 CommandKey、FaultCode（0=成功）、StartTime 和 CompleteTime

### Requirement: GetOptions RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 GetOptions 请求，返回设备支持的选项信息。

#### Scenario: 查询选项
- **WHEN** ACS 请求获取选项
- **THEN** 系统返回 OptionStruct 列表（默认返回空列表）

### Requirement: SetVouchers RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 SetVouchers 请求。

#### Scenario: 设置 Voucher
- **WHEN** ACS 下发 SetVouchers 请求
- **THEN** 系统返回 SetVouchersResponse（模拟器接受所有 Voucher）

### Requirement: Upload RPC（ACS → CPE）
系统 SHALL 响应 ACS 的 Upload 请求，模拟文件上传。

#### Scenario: 模拟上传
- **WHEN** ACS 下发 Upload 请求
- **THEN** 系统返回 UploadResponse，延迟后发送 TransferComplete

### Requirement: HTTP Digest 认证
系统 SHALL 支持 ACS 的 HTTP Digest 认证（CPE → ACS 方向）。

#### Scenario: ACS 要求 Digest 认证
- **WHEN** ACS 返回 401 Unauthorized 并携带 Digest challenge
- **THEN** 系统使用配置的 ACS 用户名和密码计算 Digest response，重新发送请求

### Requirement: CWMP HTTP 会话管理
系统 SHALL 在 CWMP 会话期间维护 HTTP Cookie（session），确保同一会话内的多次 HTTP 请求使用相同的 session cookie。

#### Scenario: 会话 Cookie 维护
- **WHEN** ACS 在 InformResponse 中设置 Set-Cookie
- **THEN** 系统在同一会话的后续所有 HTTP POST 请求中携带该 Cookie
