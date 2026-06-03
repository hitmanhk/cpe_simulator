## ADDED Requirements

### Requirement: start 命令
系统 SHALL 提供 `start` 子命令，启动 CPE 模拟器，支持公网和内网穿透两种运行模式。

#### Scenario: 公网模式启动
- **WHEN** 执行 `cpe-sim start --mode public --count 100 --acs-url http://acs.example.com:7547/`
- **THEN** 系统以公网模式启动 100 个 CPE 实例，启动 Connection Request HTTP 服务器，发送首次 Inform

#### Scenario: NAT 模式启动
- **WHEN** 执行 `cpe-sim start --mode nat --count 50000 --xmpp-host xmpp.example.com --xmpp-domain iot.example.com --xmpp-password secret --acs-url http://acs.example.com:7547/`
- **THEN** 系统以 NAT 模式启动 50,000 个 CPE 实例，批量建立 XMPP 连接，启动 Connection Request 服务器，发送首次 Inform

#### Scenario: 使用配置文件启动
- **WHEN** 执行 `cpe-sim start --config ./conf/simulator.yaml`
- **THEN** 系统从配置文件读取所有参数启动，CLI 参数覆盖配置文件中的同名参数

#### Scenario: 参数校验失败
- **WHEN** 执行 `cpe-sim start --mode nat --count 100`（缺少必填的 XMPP 参数）
- **THEN** 系统输出错误信息，列出缺少的必填参数，退出码为 1

### Requirement: stop 命令
系统 SHALL 提供 `stop` 子命令，停止运行中的模拟器实例。

#### Scenario: 优雅停止
- **WHEN** 执行 `cpe-sim stop`
- **THEN** 系统通过 Unix Domain Socket 发送停止信号，模拟器执行优雅关闭流程

#### Scenario: 强制停止
- **WHEN** 执行 `cpe-sim stop --force`
- **THEN** 系统立即终止模拟器进程，不等待活跃会话结束

#### Scenario: 超时停止
- **WHEN** 执行 `cpe-sim stop --timeout 30s`
- **THEN** 系统等待最多 30 秒进行优雅关闭，超时后强制终止

### Requirement: status 命令
系统 SHALL 提供 `status` 子命令，查询运行中模拟器的状态信息。

#### Scenario: 人类可读状态输出
- **WHEN** 执行 `cpe-sim status`
- **THEN** 系统输出格式化的状态表格，包含 PID、运行时间、模式、设备在线数、XMPP 连接状态、CWMP 会话统计、资源使用情况

#### Scenario: JSON 状态输出
- **WHEN** 执行 `cpe-sim status --json`
- **THEN** 系统输出 JSON 格式的状态数据，适合 CI/CD 流水线解析

### Requirement: device 子命令组
系统 SHALL 提供 `device` 子命令组，支持运行时设备管理操作。

#### Scenario: 列出设备
- **WHEN** 执行 `cpe-sim device list --online --limit 20`
- **THEN** 系统输出在线设备列表（Device ID、Serial、Status、XMPP 状态、Last Inform 时间），最多显示 20 条

#### Scenario: 查看设备参数
- **WHEN** 执行 `cpe-sim device get cpe-00001 --path Device.DeviceInfo`
- **THEN** 系统输出该设备指定路径下的所有参数名和值

#### Scenario: 动态添加设备
- **WHEN** 执行 `cpe-sim device add --count 100`
- **THEN** 系统动态创建 100 个新 CPE 实例并上线

#### Scenario: 移除设备
- **WHEN** 执行 `cpe-sim device remove cpe-00001`
- **THEN** 系统下线并移除指定设备

#### Scenario: 触发设备 Inform
- **WHEN** 执行 `cpe-sim device inform cpe-00001`
- **THEN** 系统触发指定设备立即发送 Inform（EventCode=6 CONNECTION REQUEST）

#### Scenario: 批量触发 Inform
- **WHEN** 执行 `cpe-sim device inform --random 100`
- **THEN** 系统随机选择 100 个设备触发 Inform

### Requirement: event 子命令组
系统 SHALL 提供 `event` 子命令组，支持触发设备事件。

#### Scenario: 触发重启
- **WHEN** 执行 `cpe-sim event reboot cpe-00001`
- **THEN** 系统模拟指定设备重启（断开连接 → 延迟 → 重新上线 → BOOT Inform）

#### Scenario: 触发恢复出厂
- **WHEN** 执行 `cpe-sim event factory-reset cpe-00001`
- **THEN** 系统模拟指定设备恢复出厂设置（重置数据模型 → 断开 → 延迟 → 重新上线 → BOOT Inform）

#### Scenario: 触发参数值变更
- **WHEN** 执行 `cpe-sim event value-change cpe-00001 --param Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.IPAddress --value 192.168.1.100`
- **THEN** 系统修改指定设备的参数值，触发 VALUE CHANGE Inform

### Requirement: version 命令
系统 SHALL 提供 `version` 子命令，输出版本信息。

#### Scenario: 输出版本
- **WHEN** 执行 `cpe-sim version`
- **THEN** 系统输出版本号、构建时间、JDK 版本

### Requirement: Unix Domain Socket 管理接口
系统 SHALL 在启动时创建 Unix Domain Socket 监听，接受 CLI 子命令的管理请求，使用 JSON 协议通信。

#### Scenario: 管理接口通信
- **WHEN** CLI 子命令（stop/status/device/event）执行
- **THEN** 系统通过 `/tmp/cpe-sim.sock` 发送 JSON 请求到运行中的模拟器，接收 JSON 响应后格式化输出

#### Scenario: 模拟器未运行
- **WHEN** 执行管理命令但模拟器未运行（Socket 文件不存在）
- **THEN** 系统输出 "Simulator is not running" 错误信息，退出码为 1

### Requirement: daemon 后台运行模式
系统 SHALL 支持 `--daemon` 参数以守护进程方式运行模拟器。

#### Scenario: daemon 启动
- **WHEN** 执行 `cpe-sim start --daemon --pid-file /var/run/cpe-sim.pid --log-file /var/log/cpe-sim.log ...`
- **THEN** 系统 fork 子进程运行模拟器，父进程写入 PID 文件后退出，子进程日志重定向到指定文件

### Requirement: 前台运行时交互
系统 SHALL 在前台运行时支持键盘快捷键进行快速操作。

#### Scenario: 快捷键操作
- **WHEN** 用户在前台模式下按 `s` 键
- **THEN** 系统刷新状态显示

#### Scenario: 快捷键触发 Inform
- **WHEN** 用户按 `i` 键
- **THEN** 系统触发所有设备发送 Periodic Inform

#### Scenario: 快捷键退出
- **WHEN** 用户按 `q` 键或 Ctrl+C
- **THEN** 系统执行优雅关闭流程

### Requirement: 启动过程进度显示
系统 SHALL 在启动过程中显示进度条和状态信息。

#### Scenario: 启动进度
- **WHEN** 模拟器启动
- **THEN** 系统依次显示：数据模型加载进度 → Connection Request 服务器启动 → XMPP 连接进度条（已连接/总数）→ Inform 发送进度条（成功/失败/总数）→ 就绪提示
