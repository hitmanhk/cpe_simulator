# CPE 模拟器

高性能 TR-069 CWMP CPE 模拟器，支持最多 50,000 个并发设备实例，用于 ACS 平台压力测试和集成验证。

## 特性

- **大规模并发**：支持 50,000 个 CPE 实例同时在线，内存占用约 575MB
- **完整 TR-069 RPC 支持**：17 种 RPC 方法，包括 Inform、GetParameterValues、SetParameterValues、Download、Reboot、FactoryReset 等
- **双数据模型**：内置 TR-181 和 TR-098 支持，可通过 YAML 自定义参数子集
- **灵活部署模式**：
  - **公网模式**：CWMP over HTTP，适用于公网直连场景
  - **NAT 模式**：XMPP + CWMP，支持 NAT 穿透（TR-069 Amendment 6）
  - **XMPP 仅连接模式**：仅建立 XMPP 通道，跳过 CwmpServer 和定时 Inform，用于连接性验证
- **反向回连**：ACS 反向连接支持，共享端口 + URL 路径路由
- **现代技术栈**：Java 21 虚拟线程、Netty 4.1、Jackson、picocli
- **运行时管理**：Unix Domain Socket 接口，支持动态控制
- **CLI 界面**：完整的命令行工具，支持操作和监控
- **桌面 GUI**：现代化 JavaFX 桌面应用（Windows 安装包 / 跨平台 JAR），与 CLI 双模式共存

## 环境要求

- JDK 21 或更高版本
- Maven 3.6+

## 快速开始

### 构建

```bash
mvn clean package
```

构建产物为 `target/cpe-simulator.jar`（包含所有依赖的 fat JAR）。

### 桌面模式（GUI）

不带参数启动即进入桌面应用（双击 JAR 或安装包图标等效）：

```bash
java --enable-preview -jar target/cpe-simulator.jar
# 或显式指定
java --enable-preview -jar target/cpe-simulator.jar --gui
```

应用名称为「ACS 设备模拟器」，采用 Cupertino 主题（macOS 风格控件），支持浅色/深色一键切换，
页面切换带淡入滑动动画，顶部为灵动岛式状态胶囊（悬停展开实时摘要）。

功能页面：

- **仪表盘**：设备总数/在线数/活跃会话/累计 Inform/运行时长/JVM 内存状态卡片，Inform 速率与在线设备实时折线图
- **设备管理**（厂商分组树表）：
  - **厂商管理**：「新增厂商」携带 Manufacturer、OUI、ProductClass、回连账户密码（单一来源，不在设备级重复配置）；内置默认厂商（取配置页 OUI/ProductClass/回连凭据）；厂商凭据用于 per-厂商回连认证（HTTP 与 XMPP 路径均生效，无厂商凭据时回退全局凭据）
  - **设备池**：设备按厂商分组显示（可展开/收起）；「新增设备」仅创建（未启动状态），**引擎未启动时也可新增**；支持批量/独立配置 DeviceInfo（软件版本、硬件版本、附加版本、ProvisioningCode）
  - **生命周期分离**：「启动模拟器」仅启动基础设施（回连监听/XMPP 引擎/调度器，忽略 count）；「启动设备」对勾选设备上线（公网→ONLINE+0 BOOTSTRAP+周期调度；NAT→XMPP 建联）；「停止设备」下线
  - **勾选与过滤**：全选/厂商内全选/单选；按厂商名、OUI、型号、软件版本、硬件版本、回连 IP、ACS IP 过滤
  - **状态展示**：建联类型（公网/XMPP）、建联状态（未启动/建联中/在线/离线）、在线时间；引擎停止后设备规格池保留，重启按原序列号恢复（deviceId 不变）
- **消息监视**：CWMP RPC 消息流与 XMPP 事件流（设备/方法过滤、暂停、清空、导出 CSV）
- **日志**：全量实时日志（级别过滤/文本搜索/自动滚动）
- **协议指南**：TR-069 会话、TR-069 回连（公网/NAT）、TR-069 RPC 流程、TR-069 错误码、XMPP 建联、TLS、SASL 流程时序图，标注每条消息的方向与开始/结束特征
- **配置**：全部配置项的分组表单（每项带悬停说明，密码明文显示），实时校验，支持 YAML 加载/保存，与 CLI 配置文件格式完全兼容

在「配置」页编辑后点击顶部「启动模拟器」即可运行；运行期间配置锁定，停止后可修改。
应用图标由 `src/test/java/.../desktop/util/IconGenerator` 生成（如需调整设计，改完后运行其 main 方法重新生成 `src/main/resources/icons/` 与 `src/jpackage/app.ico`）。

### Windows 安装包

```bash
# 免安装应用目录（target/installer/CpeSimulator/，含裁剪 JRE，无需安装 Java）
mvn clean package -Pwindows-installer

# 安装向导（需先安装 WiX Toolset 3.11+）
mvn clean package -Pwindows-installer -Djpackage.type=exe
```

安装包内嵌裁剪过的 JRE 与 JavaFX 运行时，目标机器无需预装 Java；
`CpeSimulator.exe` 无参数启动 GUI，带 CLI 参数（如 `version`、`start`）则走原命令行模式。
安装到 Program Files 等只读目录时，日志自动写入用户目录 `~/.cpe-simulator/logs`。

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

公网和 NAT 普通模式启动时，每台设备会先发送一次 `0 BOOTSTRAP`。`--xmpp-only` 模式不发送启动 BOOTSTRAP，但会在每台设备 XMPP 建联成功后启动该设备的周期 CWMP Inform。

### 使用配置文件启动

```bash
java -jar target/cpe-simulator.jar start --config ./conf/simulator.yaml
```

### XMPP 仅连接模式

```bash
java -jar target/cpe-simulator.jar start \
  --mode nat \
  --count 100 \
  --xmpp-host xmpp.example.com \
  --xmpp-domain iot.example.com \
  --xmpp-password secret \
  --acs-url http://acs.example.com:7547/ \
  --xmpp-only
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
| `--bootstrap-inform-interval <seconds>` | `0 BOOTSTRAP` 上报周期 | YAML 配置 |
| `--bootstrap-inform-initial-jitter <ratio>` | `0 BOOTSTRAP` 首次调度抖动 | YAML 配置 |
| `--boot-inform-interval <seconds>` | 周期性 `1 BOOT` 上报周期 | YAML 配置 |
| `--boot-inform-initial-jitter <ratio>` | 周期性 `1 BOOT` 首次调度抖动 | YAML 配置 |
| `--periodic-inform-interval <seconds>` | `2 PERIODIC` 上报周期 | `300` |
| `--periodic-inform-initial-jitter <ratio>` | `2 PERIODIC` 首次调度抖动 | `0.3` |
| `--max-sessions <N>` | 最大并发 CWMP 会话数 | `1000` |
| `--connect-batch <N>` | XMPP 批量连接数 | `1000` |
| `--connect-batch-delay <ms>` | 批次间隔（毫秒） | `200` |
| `--boot-batch-size <N>` | 启动 BOOTSTRAP Inform 每批设备数 | `100` |
| `--boot-batch-delay <ms>` | 启动 BOOTSTRAP Inform 批次间隔（毫秒） | `1000` |
| `--boot-jitter <ratio>` | 启动 BOOTSTRAP Inform 批内随机抖动（0.0-1.0） | `0.5` |
| `--conn-req-host <IP>` | 实例 IP（出站源 IP、回连监听和通告地址） | `0.0.0.0` |
| `--conn-req-port <port>` | 回连监听端口 | `7547` |
| `--conn-req-user <user>` | 回连认证用户名 | - |
| `--conn-req-password <pass>` | 回连认证密码 | - |
| `--xmpp-host <host>` | XMPP 服务器地址 | - |
| `--xmpp-port <port>` | XMPP 服务器端口 | DIRECT TLS 为 `5223`，其他模式为 `5222` |
| `--xmpp-domain <domain>` | XMPP 域名 | - |
| `--xmpp-password <pass>` | XMPP 密码 | - |
| `--xmpp-user-prefix <prefix>` | XMPP 用户名前缀 | `cpe` |
| `--xmpp-resource <res>` | XMPP 资源名 | `sim` |
| `--xmpp-tls-mode <NONE\|DIRECT\|STARTTLS>` | XMPP TLS 建立模式 | `NONE` |
| `--xmpp-sasl-mechanism <name>` | SASL 认证机制 | `SCRAM-SHA-1` |
| `--xmpp-handshake-timeout <seconds>` | XMPP 每个握手阶段超时，超时后退避重连 | `60` |
| `--xmpp-only` | XMPP 建联模式（跳过 CwmpServer 和启动 BOOTSTRAP，建联后启动周期 Inform） | `false` |
| `--reboot-delay <seconds>` | 模拟重启延迟 | `5` |
| `--download-delay <seconds>` | 模拟下载延迟 | `10` |
| `--download-success-rate <rate>` | 下载成功率（0.0-1.0） | `0.99` |
| `--config <path>` | 配置文件路径 | - |
| `--daemon` | 后台守护进程模式 | `false` |
| `--foreground` | 前台运行（守护进程模式内部使用） | `false` |
| `--pid-file <path>` | PID 文件路径 | `/tmp/cpe-sim.pid` |
| `--log-file <path>` | 日志文件路径（守护进程模式） | - |

### 停止模拟器

```bash
cpe-sim stop              # 优雅停止（默认等待 30 秒）
cpe-sim stop -f           # 强制停止
cpe-sim stop -t 60        # 超时 60 秒后强制停止
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

配置文件分两段：`simulator`（模拟器主体配置，`logging` 嵌于其内）与顶层 `xmpp`（NAT 模式通道）。
每个配置项在 `src/main/resources/application.yaml` 中都有逐条中文注释（用途/取值/默认值），
GUI「配置」页的"保存 YAML"也生成同一格式，可直接互相加载。完整字段一览：

```yaml
simulator:
  mode: public                    # 运行模式：public（HTTP 回连）或 nat（XMPP 穿透）
  count: 100                      # CPE 实例数量
  acs-url: http://localhost:7547/ # ACS 端点地址
  model: tr181                    # 数据模型：tr181、tr098 或自定义文件路径
  inform-schedules:               # 三个事件的周期调度；interval=0 禁用，jitter 打散首次上报
    bootstrap: { interval: 86400, initial-jitter: 0.2 }
    boot:      { interval: 3600,  initial-jitter: 0.2 }
    periodic:  { interval: 300,   initial-jitter: 0.3 }
  max-sessions: 1000              # 全局最大并发 CWMP 会话（信号量限流）
  conn-req-port: 7547             # 回连监听端口（⚠ 避开 Windows 保留端口段，见文件内注释）
  logging:
    level: INFO                   # 注意：logging 必须嵌在 simulator 内，顶层 logging 不生效

xmpp:                             # NAT 模式必填
  host: localhost
  domain: iot.example.com
  tls-mode: NONE                  # NONE | DIRECT（5223）| STARTTLS（5222）
  sasl-mechanism: SCRAM-SHA-1
  reconnect-initial-delay: 60     # 首次重试随机上限（秒）
  reconnect-max-delay: 1200       # 指数增长的封顶上限（秒）
```

XMPP 端口未显式配置时，`DIRECT` TLS 默认使用 `5223`，`NONE` 和 `STARTTLS` 默认使用 `5222`。可通过 `--xmpp-port` 或 YAML 中的 `xmpp.port` 覆盖默认值。

同一执行机通过多个网卡运行多个模拟器时，为每个进程配置不同的 `conn-req-host`。
该地址会统一用于 CWMP/XMPP 出站源 IP、回连服务监听地址和上报给 ACS 的
`ConnectionRequestURL`，因此多个实例可以共用 `7547` 端口：

```bash
java -jar target/cpe-simulator.jar start --conn-req-host 192.0.2.11 --socket-path /tmp/cpe-sim-11.sock
java -jar target/cpe-simulator.jar start --conn-req-host 192.0.2.12 --socket-path /tmp/cpe-sim-12.sock
```

### 自定义数据模型

内置模型的文件组织：`models/tr181.yaml` / `models/tr098.yaml` 存放业务参数
（ManagementServer/WAN/WiFi/VOIP/XMPP 等）及各模型独有的 DeviceInfo 参数；
两个模型**公共的** DeviceInfo 参数统一定义在共享文件 `models/deviceinfo.yaml`
（路径相对书写，ModelLoader 加载时按模型根对象 `Device.` / `InternetGatewayDevice.` 自动补前缀合并）。
自定义模型（`--model` 指定文件路径）为单文件自包含，DeviceInfo 直接内联即可。

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
| `DeviceRegistry` | 并发设备注册表，处理回连请求路由 |
| `BatchConnector` | 批量设备状态初始化 |
| `BootLauncher` | BOOTSTRAP Inform 阶梯式分批启动，带 jitter 抖动 |
| `CpeDevice` | 单个 CPE 实例，包含 DataModel 和会话状态 |
| `CwmpSession` | CWMP 会话状态机（IDLE → INFORM_SENT → CPE_HOLD → ACS_HOLD） |
| `CwmpClient` | CPE→ACS 的 HTTP 客户端，支持 Digest 认证 |
| `CwmpServer` | ACS→CPE 反向回连的 Netty HTTP 服务器 |
| `XmppEngine` | NAT 模式下的 XMPP 连接管理 |
| `ReconnectScheduler` | XMPP Full Jitter 指数退避（上限从 60s 翻倍至 1200s，每轮在 0～上限间随机） |
| `InformScheduler` | 定时 Inform 调度，支持随机抖动 |
| `RpcRegistry` | 17 种 RPC 方法处理器注册表 |
| `ResourceMonitor` | JVM 内存、线程、Socket 资源监控 |

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
| XMPP | Netty + Aalto XML | 轻量 XMPP 客户端（直接 TLS/STARTTLS、SASL PLAIN/SCRAM-SHA-1/SCRAM-SHA-256、Bind、Presence、IQ、Ping、指数退避重连） |
| HTTP | JDK HttpClient + Netty | 虚拟线程 HTTP 客户端、Netty HTTP 服务器 |

### 桌面层（GUI 模式）

| 组件 | 说明 |
|------|------|
| `desktop/DesktopLauncher` | GUI 入口（`CpeSimulatorApp` 无参数时分发到此） |
| `desktop/DesktopApp` | JavaFX 应用：Cupertino 主题 + 深浅色切换 + 窗口图标 |
| `desktop/ui/MainWindow` | 主窗口：品牌区 + 灵动岛状态胶囊（StatusIsland）+ 图标侧边导航 + 动画页面栈 |
| `desktop/ui/*View` | 仪表盘/设备管理/设备详情/消息监视/日志/协议指南/配置编辑器 |
| `desktop/ui/SequenceDiagram` | 自绘协议时序图组件（协议指南页数据源为 ProtocolFlows） |
| `desktop/bridge/EngineController` | 引擎生命周期控制（虚拟线程执行 start/stop），配置校验与 CLI start 一致，启动失败给出可操作建议（如端口被系统保留） |
| `desktop/bridge/UiEventBridge` | 引擎事件 → FX 线程节流管线（250ms 批量合并，防洪泛） |
| `desktop/bridge/UiLogAppender` | logback 桥接（编程式注册，不改 logback.xml），驱动实时日志视图 |
| `common/event/SimEventHub` | 引擎事件总线（可选监听，CLI 模式零开销）；`SimLogger` 结构化方法发布事件 |
| `resources/desktop/app.css` | 自定义主题（毛玻璃侧边栏/卡片/灵动岛，`:dark` 伪类切换深浅色） |

## 测试

运行所有测试：

```bash
mvn test
```

测试覆盖：

- **单元测试**：所有核心组件（191 个测试）
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
- BOOTSTRAP Inform 阶梯式分批启动（`boot-batch-size` + `boot-batch-delay` + `boot-jitter`），避免瞬间冲击 ACS
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
