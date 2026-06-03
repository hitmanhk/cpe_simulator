## Why

TR-069 (CWMP) 协议是 CPE 设备管理的行业标准，但缺乏高性能的 CPE 模拟器用于 ACS 压力测试和集成验证。现有方案要么不支持大规模连接（5万+），要么不支持 XMPP 穿透场景（TR-069 Amendment 6），无法满足现代 CPE 管理平台的测试需求。

## What Changes

- 新建高性能 CWMP CPE 模拟器，支持最多 50,000 个并发 CPE 实例
- 实现完整的 TR-069 RPC 方法集（Inform、GetParameterValues、SetParameterValues、Download、Reboot 等 17+ 方法）
- 支持 TR-181 和 TR-098 两种数据模型，通过 YAML 配置文件可定制参数子集
- 实现基于 Netty 的轻量 XMPP 客户端，支持 TR-069 Amendment 6 定义的内网穿透场景
- 支持两种运行模式：公网直连（CWMP only）和内网穿透（XMPP + CWMP）
- 实现 ACS 反向回连（Connection Request），支持共享端口 + URL 路径区分方案
- 提供完整的 CLI 界面，支持启动、停止、状态查询、设备管理、事件触发等操作
- 支持 Unix Domain Socket 进程间通信，实现运行时动态控制
- 支持 daemon 后台运行模式，适配 CI/CD 集成场景

## Capabilities

### New Capabilities

- `cwmp-protocol`: CWMP SOAP 协议引擎，包括 SOAP 消息构建/解析、会话状态机、所有 TR-069 RPC 方法实现、HTTP 客户端/服务器
- `xmpp-protocol`: 基于 Netty 的轻量 XMPP 客户端，包括 XML Stream 编解码、SASL 认证、Presence 管理、IQ stanza 处理、Ping/Pong keepalive、断线重连
- `data-model`: TR-181/TR-098 数据模型管理，包括 YAML 配置加载、参数树结构、模板变量解析、运行时参数读写
- `simulation-engine`: 模拟引擎核心，包括设备批量创建/销毁、生命周期管理、Inform 调度、并发控制、资源调度
- `cli-interface`: 命令行界面，包括 picocli 命令定义、运行时交互、Unix Domain Socket 管理接口、daemon 模式
- `connection-request`: ACS 反向回连支持，包括 HTTP 服务器监听、共享端口路由、设备注册表、公网/XMPP 两种模式的回连处理

### Modified Capabilities

（无，这是全新项目）

## Impact

- **技术栈**: Java 21（虚拟线程）、Netty 4.1、Jackson XML/YAML、picocli、SLF4J/Logback
- **构建**: Maven 单模块项目，打包为 fat JAR
- **部署**: 单 JAR 文件，JVM 参数 `-Xmx3g` 可支持 50K 连接
- **依赖**: 需要 JDK 21+ 运行环境
- **网络**: 需要能够访问 ACS HTTP 端点和 XMPP 服务器（NAT 模式）
- **存储**: 无持久化需求，所有状态在内存中
