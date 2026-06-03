## ADDED Requirements

### Requirement: CPE 实例批量创建
系统 SHALL 支持一次性创建指定数量的 CPE 实例，每个实例拥有唯一的设备标识（OUI + ProductClass + SerialNumber）和独立的数据模型副本。

#### Scenario: 创建 1000 个 CPE 实例
- **WHEN** 用户指定 `--count 1000`
- **THEN** 系统创建 1000 个 CPE 实例，序列号从 `SIM00001` 到 `SIM01000`，每个实例拥有独立的数据模型参数树

#### Scenario: 自定义设备标识前缀
- **WHEN** 用户指定 `--oui AABBCC --serial-prefix DEV --product-class Router-X1`
- **THEN** 系统创建的设备标识为 OUI=AABBCC, ProductClass=Router-X1, SerialNumber=DEV00001~DEV01000

### Requirement: CPE 实例生命周期管理
系统 SHALL 管理 CPE 实例的完整生命周期：初始化 → XMPP 连接（NAT 模式）→ 首次 Inform → 周期运行 → 关闭。

#### Scenario: 正常启动流程
- **WHEN** 模拟器启动
- **THEN** 系统按顺序执行：加载数据模型 → 创建 CPE 实例 → 启动 Connection Request 服务器 → 建立 XMPP 连接（NAT 模式）→ 发送首次 Inform（EventCode=BOOT）→ 进入周期运行

#### Scenario: 优雅关闭流程
- **WHEN** 收到停止信号
- **THEN** 系统停止新的 Inform 调度 → 等待活跃 CWMP 会话自然结束（最多 30 秒超时）→ 断开所有 XMPP 连接 → 关闭 Connection Request 服务器 → 退出进程

### Requirement: Inform 定时调度
系统 SHALL 按配置的间隔定期触发每个 CPE 实例的 Periodic Inform，支持随机抖动避免惊群效应。

#### Scenario: 定时 Inform（含抖动）
- **WHEN** 配置 `--inform-interval 300 --inform-jitter 0.3`
- **THEN** 每个 CPE 实例的 Inform 间隔为 300 ± 90 秒（随机），50K 设备的 Inform 请求均匀分布在时间轴上

#### Scenario: 首次 Inform
- **WHEN** CPE 实例首次启动
- **THEN** 系统发送 EventCode 为 "1 BOOT" 的 Inform（而非 PERIODIC）

### Requirement: 并发 CWMP 会话控制
系统 SHALL 通过 Semaphore 限制同时活跃的 CWMP 会话数量，防止过多并发会话压垮 ACS。

#### Scenario: 并发限制
- **WHEN** 配置 `--max-sessions 1000` 且当前已有 1000 个活跃会话
- **THEN** 新的 Inform 请求排队等待，直到有会话释放后才发送

#### Scenario: 会话释放
- **WHEN** 一个 CWMP 会话正常结束（收到 204 No Content）
- **THEN** 系统释放 Semaphore 许可，允许排队中的下一个 Inform 请求发送

### Requirement: 批量连接速率控制
系统 SHALL 在启动时按批次建立 XMPP 连接（NAT 模式），控制每批连接数和批次间隔。

#### Scenario: 批量连接
- **WHEN** 配置 `--connect-batch 1000 --connect-batch-delay 200`
- **THEN** 系统每批建立 1000 个 XMPP 连接，批次间隔 200ms，50K 连接总耗时约 10 秒

#### Scenario: 连接失败处理
- **WHEN** 某批连接中有部分失败
- **THEN** 系统记录失败数量和原因，失败的设备进入重连队列，不影响其他批次继续

### Requirement: 运行时设备动态增减
系统 SHALL 支持在模拟器运行期间动态添加或移除 CPE 实例。

#### Scenario: 动态添加设备
- **WHEN** 通过管理接口请求添加 100 个设备
- **THEN** 系统创建 100 个新 CPE 实例（序列号从当前最大值继续），建立 XMPP 连接（NAT 模式），发送 BOOT Inform

#### Scenario: 动态移除设备
- **WHEN** 通过管理接口请求移除指定设备
- **THEN** 系统关闭该设备的 CWMP 会话和 XMPP 连接，从设备注册表中移除，释放资源

### Requirement: 模拟行为配置
系统 SHALL 支持配置各种模拟行为的参数（重启延迟、下载延迟、成功率等）。

#### Scenario: 配置重启延迟
- **WHEN** 配置 `reboot-delay: 5`
- **THEN** 收到 Reboot RPC 后，系统延迟 5 秒完成模拟重启，然后发送 BOOT Inform

#### Scenario: 配置下载成功率
- **WHEN** 配置 `download-success-rate: 0.99`
- **THEN** 99% 的 Download 模拟成功（TransferComplete FaultCode=0），1% 模拟失败

### Requirement: 资源监控与统计
系统 SHALL 收集和统计运行时的关键指标，通过管理接口提供查询。

#### Scenario: 查询运行统计
- **WHEN** 通过管理接口请求 status
- **THEN** 系统返回：在线/离线设备数、活跃会话数、XMPP 连接状态、Inform 发送统计、RPC 接收统计、JVM 内存使用、线程数、Socket 数
