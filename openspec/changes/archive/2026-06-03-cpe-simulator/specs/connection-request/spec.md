## ADDED Requirements

### Requirement: Connection Request HTTP 服务器
系统 SHALL 启动一个 HTTP 服务器监听反向回连请求，使用 Netty HTTP codec 实现，所有 CPE 实例共享同一端口。

#### Scenario: 启动监听
- **WHEN** 模拟器启动，配置 `--conn-req-port 7547 --conn-req-host 0.0.0.0`
- **THEN** 系统在指定地址和端口启动 HTTP 服务器，准备接收 ACS 的反向回连请求

#### Scenario: 端口被占用
- **WHEN** 配置的端口已被其他进程占用
- **THEN** 系统输出错误信息 "Port 7547 is already in use"，退出码为 2

### Requirement: 共享端口路由
系统 SHALL 通过 URL 路径 `/connReq/{deviceId}` 将反向回连请求路由到对应的 CPE 实例。

#### Scenario: 路由到指定设备
- **WHEN** ACS 发送 `GET http://sim-ip:7547/connReq/cpe-00001`
- **THEN** 系统从设备注册表中查找 `cpe-00001`，触发该设备发送 EventCode 为 "6 CONNECTION REQUEST" 的 Inform

#### Scenario: 设备不存在
- **WHEN** ACS 请求的 deviceId 在注册表中不存在
- **THEN** 系统返回 HTTP 404 Not Found

#### Scenario: 设备离线
- **WHEN** ACS 请求的设备当前处于离线状态
- **THEN** 系统返回 HTTP 503 Service Unavailable，body 包含 "Device is offline"

### Requirement: 设备注册表
系统 SHALL 维护一个并发安全的设备注册表（ConcurrentHashMap），支持通过 deviceId 快速查找 CPE 实例。

#### Scenario: 注册设备
- **WHEN** CPE 实例创建并上线
- **THEN** 系统将设备 ID 和实例引用注册到注册表中

#### Scenario: 注销设备
- **WHEN** CPE 实例下线或被移除
- **THEN** 系统从注册表中移除该设备的注册信息

#### Scenario: 并发查找
- **WHEN** 多个 Connection Request 同时到达
- **THEN** 系统并发安全地查找设备，不产生竞态条件

### Requirement: 公网模式反向回连
系统 SHALL 在公网模式下，ACS 通过 HTTP 直接访问 CPE 的 Connection Request URL 触发回连。

#### Scenario: 公网回连流程
- **WHEN** 公网模式下 ACS 发送 `GET /connReq/{deviceId}`
- **THEN** 系统触发目标设备立即发送 Inform（EventCode=6），设备通过 HTTP POST 主动连接 ACS 建立 CWMP 会话

### Requirement: NAT 模式反向回连（XMPP）
系统 SHALL 在 NAT 模式下，ACS 通过 XMPP Server 发送 IQ stanza 触发 CPE 回连。

#### Scenario: XMPP 回连流程
- **WHEN** NAT 模式下 ACS 通过 XMPP 发送 Connection Request IQ stanza
- **THEN** 系统收到 IQ 后回复 result，触发目标设备立即发送 Inform（EventCode=6），设备通过 HTTP POST 主动连接 ACS 建立 CWMP 会话

### Requirement: Connection Request HTTP 认证
系统 SHALL 支持对 ACS 的反向回连请求进行 HTTP 认证（可选）。

#### Scenario: 无认证模式
- **WHEN** 未配置 `--conn-req-user` 和 `--conn-req-password`
- **THEN** 系统接受所有 Connection Request 请求，不进行认证

#### Scenario: 认证模式
- **WHEN** 配置了 `--conn-req-user admin --conn-req-password secret`
- **THEN** 系统要求 Connection Request 请求携带 HTTP Basic 或 Digest 认证，未认证返回 401 Unauthorized

### Requirement: Connection Request URL 自动配置
系统 SHALL 在 CPE 实例初始化时，自动将 Connection Request URL 写入数据模型的 `ManagementServer.ConnectionRequestURL` 参数。

#### Scenario: 自动设置回连 URL
- **WHEN** CPE 实例初始化，配置 `--conn-req-host 192.168.1.100 --conn-req-port 7547`
- **THEN** 系统将 `Device.ManagementServer.ConnectionRequestURL`（TR-181）或 `InternetGatewayDevice.ManagementServer.ConnectionRequestURL`（TR-098）设置为 `http://192.168.1.100:7547/connReq/{deviceId}`
