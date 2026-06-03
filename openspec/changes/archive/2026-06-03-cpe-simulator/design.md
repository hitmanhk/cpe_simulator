## Context

TR-069 (CWMP) 是 CPE 设备管理的行业标准协议。当前项目需要构建一个高性能 CPE 模拟器，用于 ACS 平台的压力测试和集成验证。模拟器需要支持最多 50,000 个并发 CPE 实例，同时支持公网直连和内网穿透（XMPP）两种部署场景。

项目从零开始构建，无历史代码包袱。目标用户为开发/测试工程师和 CI/CD 流水线。

## Goals / Non-Goals

**Goals:**

- 支持 50,000 个并发 CPE 实例同时在线
- 实现 TR-069 定义的全部 RPC 方法（17+ 种）
- 支持 TR-181 和 TR-098 数据模型，参数子集可通过 YAML 配置
- 支持公网模式（CWMP only）和 NAT 模式（XMPP + CWMP）
- 支持 ACS 反向回连（Connection Request）
- 提供完整的 CLI 界面和运行时管理接口
- 单 JAR 部署，内存占用 ≤ 3GB（50K 连接时）

**Non-Goals:**

- 不实现完整的 XMPP 协议（仅实现 TR-069 所需子集）
- 不实现 TR-069 的所有 EventCode（仅实现常用类型：0/1/2/4/6/7/M系列）
- 不持久化设备状态（重启后重新初始化）
- 不实现真正的固件下载/上传（仅模拟行为）
- 不实现 TR-369 (USP) 协议
- 不做 GUI 界面
- 不做多节点分布式部署

## Decisions

### 1. 语言: Java 21 + 虚拟线程

**选择**: Java 21，使用虚拟线程（Virtual Threads）处理并发。

**理由**:
- 用户为 Java 开发者，生态熟悉度高
- 虚拟线程使 50K 连接的编程模型变为同步直白风格，开发效率高
- JDK 21 的 HttpClient 内置支持虚拟线程，无需额外 HTTP 框架
- 虚拟线程在 IO 密集场景下几乎零开销

**备选方案**:
- Go: goroutine 天然适合，但用户不熟悉
- Rust: tokio 性能极致，但开发周期长
- Erlang/Elixir: 天生擅长长连接，但生态小众

### 2. XMPP 客户端: Netty 自建（非 Smack）

**选择**: 基于 Netty 自建轻量 XMPP 客户端，仅实现 TR-069 所需子集。

**理由**:
- Smack 每个连接约 40-50KB 内存 + 多个后台线程，50K 连接 ≈ 3-4GB
- Netty 共享 EventLoop，50K 连接 ≈ 1-1.5GB（节省约 50%）
- TR-069 仅使用 XMPP 的极小子集（Stream/SASL/Bind/Presence/IQ/Ping），自建工作量可控
- Netty EventLoop + 虚拟线程协作：IO 在 EventLoop，业务逻辑在虚拟线程

**备选方案**:
- Smack 4.x: 最成熟的 Java XMPP 库，但内存开销大，内部线程模型与虚拟线程不兼容
- 先用 Smack 后迁移: 增加技术债，不如一步到位

### 3. CWMP SOAP: 手写 XML（非 SOAP 框架）

**选择**: 手写 SOAP XML 构建/解析，使用 Jackson XML 辅助。

**理由**:
- TR-069 的 SOAP 结构固定且简单，不需要完整 SOAP 栈
- Apache CXF / JAX-WS 等框架过重，引入不必要的复杂性
- StringBuilder + 预编译 XML 片段性能最优
- Jackson XML 用于解析 ACS 下发的 SOAP 消息，足够灵活

**备选方案**:
- Apache CXF: 功能完整但太重
- JAX-WS: 标准但不够灵活

### 4. 构建工具: Maven 单模块

**选择**: Maven 单模块项目，通过包结构维护模块边界。

**理由**:
- 项目体量适中，单模块足够
- 用户对 Maven 更熟悉
- 多模块增加初始复杂度，收益不大
- 如果后续 XMPP/CWMP 有独立复用需求，可以再拆分

**备选方案**:
- Maven 多模块: 强制模块边界，但初始搭建复杂
- Gradle: 构建更快，但学习曲线更高

### 5. CLI: picocli + Unix Domain Socket

**选择**: picocli 做命令行解析，Unix Domain Socket 做进程间通信。

**理由**:
- picocli 是 Java CLI 的事实标准，支持子命令、自动补全、彩色输出
- UDS 零网络开销，JDK 16+ 内置支持，安全性好（文件权限控制）
- 管理接口协议为简单 JSON 请求/响应

**备选方案**:
- HTTP API: 通用但多一个端口，安全风险
- JMX: 太重，外部调用不方便

### 6. 反向回连: 共享端口 + URL 路径

**选择**: 所有 CPE 共享一个监听端口，通过 URL 路径 `/connReq/{deviceId}` 区分设备。

**理由**:
- 50K 设备不可能分配 50K 个端口
- 单端口方案简单可靠
- ACS 侧配置 ConnectionRequestURL 时带上设备 ID 即可

**备选方案**:
- 每设备独立端口: 不可行（端口数限制）
- 每设备独立 IP: 需要大量 IP 资源

### 7. 数据模型: YAML 可配置

**选择**: 通过 YAML 文件定义参数子集，支持模板变量。

**理由**:
- TR-181 有 2000+ 参数，TR-098 有 800+ 参数，完整实现不现实
- YAML 人类可读，易于版本控制
- 模板变量（如 `${random.uuid}`、`${acs.url}`）支持动态生成设备特有值
- 内置 TR-181 和 TR-098 默认子集，用户可自定义

**备选方案**:
- 硬编码: 不灵活，修改需重新编译
- JSON: 可读性不如 YAML
- 数据库: 过重，无持久化需求

### 8. HTTP Server: Netty HTTP Codec

**选择**: 反向回连的 HTTP 服务器使用 Netty 的 HTTP codec。

**理由**:
- 已引入 Netty（XMPP 用），统一技术栈
- 共享 EventLoop，资源利用最优
- 回连 HTTP 服务很简单（只需响应 GET 请求），不需要高级路由框架

**备选方案**:
- Undertow: 高级 API 但引入额外依赖和线程模型
- JDK HttpServer: 零依赖但功能基础

## Risks / Trade-offs

**[风险] Netty XMPP 客户端开发量大** → 仅实现 TR-069 所需子集（Stream/SASL/Bind/Presence/IQ/Ping），预计 5-6 个核心能力，工作量可控。优先实现 PLAIN 认证，SCRAM-SHA-1 后续迭代。

**[风险] XML Stream 分帧复杂** → 使用 Aalto XML（异步 StAX 解析器）处理 XML 分帧，正确处理 CDATA、命名空间、自关闭标签等边界情况，避免手写 XML 解析器的 bug。

**[风险] 50K 并发连接对 ACS 造成压力** → 实现 Inform 发送速率控制：随机抖动避免惊群，全局 Semaphore 限制并发会话数（默认 1000），批量连接速率控制。

**[风险] 虚拟线程 pinning** → 确保业务逻辑中不使用 synchronized 块（使用 ReentrantLock 替代），避免虚拟线程 pin 到平台线程。

**[权衡] 单模块 vs 多模块** → 单模块牺牲了强制模块边界，但换来简单性和快速上手。通过包结构 + 代码审查维护边界，后续可拆分。

**[权衡] 不持久化状态** → 重启后所有设备状态丢失，但简化了实现，符合模拟器"用完即弃"的使用场景。

**[权衡] 手写 SOAP vs SOAP 框架** → 手写牺牲了一些开发便利性，但换来极致性能和零依赖。TR-069 的 SOAP 足够简单，手写风险可控。
