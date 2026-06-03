# CPE 模拟器

高性能 TR-069 CWMP CPE 模拟器，支持最多 50,000 个并发设备实例，用于 ACS 平台压力测试和集成验证。

## 特性

- **大规模并发**：支持 50,000 个 CPE 实例同时在线，内存占用约 575MB
- **完整 TR-069 RPC 支持**：17+ 种 RPC 方法，包括 Inform、GetParameterValues、SetParameterValues、Download、Reboot、FactoryReset 等
- **双数据模型**：内置 TR-181 和 TR-098 支持，可通过 YAML 自定义参数子集
- **灵活部署模式**：
  - **公网模式**：CWMP over HTTP，适用于公网直连场景
  - **NAT 模式**：XMPP + CWMP，支持 NAT 穿透（TR-069 Amendment 6）
- **反向回连**：ACS 反向连接支持，共享端口 + URL 路径路由
- **现代技术栈**：Java 21 虚拟线程、Netty 4.1、Jackson、picocli
- **运行时管理**：Unix Domain Socket 接口，支持动态控制
- **CLI 界面**：完整的命令行工具，支持操作和监控

## 环境要求

- JDK 21 或更高版本
- Maven 3.6+

## 快速开始

### 构建

```bash
mvn clean package
```

构建产物为 `target/cpe-simulator.jar`（包含所有依赖的 fat JAR）。

### 公网模式启动

```bash
java -jar target/cpe-simulator.jar start \
  --mode public \
  --count 100 \
  --acs-url http://acs.example.com:7547/ \
  --acs-user admin \
  --acs-password admin
```

### NAT 模式启动

```bash
java -jar target/cpe-simulator.jar start \
  --mode nat \
  --count 50000 \
  --xmpp-host xmpp.example.com \
  --xmpp-domain iot.example.com \
  --xmpp-password secret \
  --acs-url http://acs.example.com:7547/
```

### 使用配置文件启动

```bash
java -jar target/cpe-simulator.jar start --config ./conf/simulator.yaml
```

## CLI 命令

### 启动模拟器

```bash
cpe-sim start [选项]
```

主要选项：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--mode <public\|nat>` | 运行模式 | `public` |
| `--count <N>` | CPE 实例数量 | `100` |
| `--acs-url <URL>` | ACS 端点地址（必填） | - |
| `--acs-user <user>` | ACS 用户名 | `admin` |
| `--acs-password <pass>` | ACS 密码 | `admin` |
| `--model <tr181\|tr098\|path>` | 数据模型 | `tr181` |
| `--oui <OUI>` | OUI 前缀 | `000000` |
| `--product-class <class>` | 产品类别 | `SimCPE` |
| `--serial-prefix <prefix>` | 序列号前缀 | `SIM` |
| `--inform-interval <seconds>` | 定时 Inform 间隔（秒） | `300` |
| `--inform-jitter <ratio>` | Inform 抖动比例（0.0-1.0） | `0.3` |
| `--max-sessions <N>` | 最大并发 CWMP 会话数 | `1000` |
| `--connect-batch <N>` | XMPP 批量连接数 | `1000` |
| `--connect-batch-delay <ms>` | 批次间隔（毫秒） | `200` |
| `--conn-req-host <host>` | 回连监听地址 | `0.0.0.0` |
| `--conn-req-port <port>` | 回连监听端口 | `7547` |
| `--xmpp-host <host>` | XMPP 服务器地址 | - |
| `--xmpp-port <port>` | XMPP 服务器端口 | `5222` |
| `--xmpp-domain <domain>` | XMPP 域名 | - |
| `--xmpp-password <pass>` | XMPP 密码 | - |
| `--reboot-delay <seconds>` | 模拟重启延迟 | `5` |
| `--download-delay <seconds>` | 模拟下载延迟 | `10` |
| `--download-success-rate <rate>` | 下载成功率（0.0-1.0） | `0.99` |
| `--config <path>` | 配置文件路径 | - |
| `--daemon` | 后台守护进程模式 | `false` |

### 停止模拟器

```bash
cpe-sim stop              # 优雅停止（默认等待 30 秒）
cpe-sim stop --force      # 强制停止
cpe-sim stop --timeout 60 # 超时 60 秒后强制停止
```

### 查询状态

```bash
cpe-sim status        # 人类可读表格输出
cpe-sim status --json # JSON 格式输出
```

### 设备管理

```bash
cpe-sim device list [--online] [--limit <N>]           # 列出设备
cpe-sim device get <deviceId> [--path <参数路径>]       # 查看设备参数
cpe-sim device add --count <N>                          # 动态添加设备
cpe-sim device remove <deviceId>                        # 移除设备
cpe-sim device inform <deviceId>                        # 触发指定设备 Inform
cpe-sim device inform --random <N>                      # 随机触发 N 个设备 Inform
```

### 事件触发

```bash
cpe-sim event reboot <deviceId>                                    # 模拟设备重启
cpe-sim event factory-reset <deviceId>                             # 模拟恢复出厂设置
cpe-sim event value-change <deviceId> --param <路径> --value <值>  # 触发参数变更 Inform
```

### 版本信息

```bash
cpe-sim version
```

## 配置说明

### 配置文件（application.yaml）

```yaml
simulator:
  mode: public                    # 运行模式：public 或 nat
  count: 100                      # CPE 实例数量
  acs-url: http://localhost:7547/ # ACS 端点地址
  acs-user: admin                 # ACS 认证用户名
  acs-password: admin             # ACS 认证密码
  model: tr181                    # 数据模型：tr181、tr098 或自定义文件路径
  oui: "000000"                   # OUI 前缀
  product-class: SimCPE           # 产品类别
  serial-prefix: SIM              # 序列号前缀
  inform-interval: 300            # 定时 Inform 间隔（秒）
  inform-jitter: 0.3              # Inform 抖动比例
  max-sessions: 1000              # 最大并发会话数
  conn-req-host: 0.0.0.0         # 回连监听地址
  conn-req-port: 7547             # 回连监听端口
  reboot-delay: 5                 # 模拟重启延迟（秒）
  download-delay: 10              # 模拟下载延迟（秒）
  download-success-rate: 0.99     # 下载成功率

xmpp:
  host: localhost                 # XMPP 服务器地址
  port: 5222                      # XMPP 服务器端口
  domain: iot.example.com         # XMPP 域名
  user-prefix: cpe                # 用户名前缀
  password: secret                # 密码
  resource: sim                   # 资源名
  tls: false                      # 是否启用 TLS
  sasl-mechanism: PLAIN           # SASL 认证机制：PLAIN 或 SCRAM-SHA-1
  keepalive-interval: 60          # Ping 间隔（秒）
  keepalive-timeout: 30           # Ping 超时（秒）
```

### 自定义数据模型

创建 YAML 文件定义参数：

```yaml
parameters:
  - path: Device.DeviceInfo.Manufacturer
    type: xsd:string
    value: "MyVendor"
    writable: false
    notification: 0
  - path: Device.ManagementServer.URL
    type: xsd:string
    value: "${acs.url}"
    writable: true
  - path: Device.ManagementServer.PeriodicInformInterval
    type: xsd:unsignedInt
    value: "300"
    writable: true
    notification: 0
```

支持的模板变量：

| 变量 | 说明 |
|------|------|
| `${random.uuid}` | 每个设备唯一的 UUID |
| `${device.serial}` | 设备序列号 |
| `${device.oui}` | 设备 OUI |
| `${device.productClass}` | 设备产品类别 |
| `${acs.url}` | 配置文件中的 ACS 地址 |
| `${random.ip}` | 随机 IP 地址（10.x.x.x） |
| `${conn.req.url}` | 该设备的回连 URL |

## 架构概览

### 核心组件

| 组件 | 说明 |
|------|------|
| `SimulationEngine` | 主编排器，管理设备生命周期 |
| `DeviceManager` | CPE 实例的批量创建和管理 |
| `CpeDevice` | 单个 CPE 实例，包含 DataModel 和会话状态 |
| `CwmpSession` | CWMP 会话状态机（IDLE → INFORM_SENT → CPE_HOLD → ACS_HOLD） |
| `CwmpClient` | CPE→ACS 的 HTTP 客户端，支持 Digest 认证 |
| `CwmpServer` | ACS→CPE 反向回连的 Netty HTTP 服务器 |
| `XmppEngine` | NAT 模式下的 XMPP 连接管理 |
| `InformScheduler` | 定时 Inform 调度，支持随机抖动 |
| `RpcRegistry` | 17+ 种 RPC 方法处理器注册表 |

### 数据模型层

| 组件 | 说明 |
|------|------|
| `DataModel` | 参数树结构，支持 TR-181/TR-098 |
| `ModelLoader` | YAML 模型加载和模板变量解析 |
| `ParameterNode` | 树节点：值、类型、可写标志、通知属性 |
| `ModelTemplate` | 模板变量解析引擎 |

### 协议层

| 层 | 技术 | 说明 |
|----|------|------|
| CWMP | StringBuilder + Jackson XML | SOAP 消息构建/解析、会话管理、RPC 处理 |
| XMPP | Netty + Aalto XML | 轻量 XMPP 客户端（Stream、SASL、Bind、Presence、IQ、Ping） |
| HTTP | JDK HttpClient + Netty | 虚拟线程 HTTP 客户端、Netty HTTP 服务器 |

## 测试

运行所有测试：

```bash
mvn test
```

测试覆盖：

- **单元测试**：所有核心组件（167 个测试）
- **集成测试**：公网模式和 NAT 模式端到端流程
- **并发压力测试**：100 个设备、Semaphore 限流验证
- **内存估算测试**：每设备约 11.8 KB（TR-181）

## 性能指标

### 内存占用

| 数据模型 | 每设备内存 | 50K 设备预估 |
|----------|-----------|-------------|
| TR-181 | ~11.8 KB | ~575 MB |
| TR-098 | ~7.8 KB | ~380 MB |

JVM 推荐参数：`-Xmx3g`（50K 设备场景）

### 并发控制

- 虚拟线程处理 IO 密集型操作（HTTP、XMPP）
- Semaphore 限制并发会话数（可配置 `max-sessions`）
- 批量连接速率控制（`connect-batch` + `connect-batch-delay`）
- Inform 调度随机抖动，避免惊群效应

## 运行时交互

前台模式下支持键盘快捷键：

| 按键 | 功能 |
|------|------|
| `s` | 打印状态信息 |
| `i` | 触发所有设备 Inform |
| `r` | 触发随机设备重启 |
| `q` | 退出（优雅关闭） |
| `h` | 打印帮助信息 |

## 管理接口

模拟器启动后在 `/tmp/cpe-sim.sock` 创建 Unix Domain Socket 监听。所有 CLI 管理命令（除 `start` 外）通过该接口与运行中的模拟器通信，使用 JSON 协议。

## 守护进程模式

```bash
java -jar target/cpe-simulator.jar start \
  --daemon \
  --pid-file /var/run/cpe-sim.pid \
  --log-file /var/log/cpe-sim.log \
  --mode public \
  --count 1000 \
  --acs-url http://acs.example.com:7547/
```

## 许可证

详见 LICENSE 文件。
