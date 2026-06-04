## Context

CPE Simulator 当前通过 `--daemon` 选项实现后台运行：`DaemonHelper.daemonize()` 用 ProcessBuilder 启动子进程（去掉 `--daemon`、加上 `--foreground`），父进程写 PID 文件后退出，子进程启动后再覆盖写入正确 PID。这个机制存在以下问题：

- PID 文件写入有竞争条件：父进程先写错误 PID，子进程后覆盖，中间窗口期 stop 命令会读错
- `getJarPath()` 通过反射猜测 JAR 路径，IDE 运行或 classpath 模式下不可靠
- `--foreground` 作为内部标志暴露给用户
- 整套逻辑约 97 行代码只为实现一次 fork，而 Unix 下 `nohup` 天然完成同样功能

`stop` 命令已完全通过 UDS socket 停机，不依赖 PID 文件。PID 文件仅被 shutdown hook 的 `deletePidFile` 消费，属信息性而非功能性。

## Goals / Non-Goals

**Goals:**
- 删除 daemon 模式及相关代码（DaemonHelper、daemon/foreground/pidFile/logFile 选项和字段）
- 简化 StartCommand 为纯前台模式
- 保持 UDS 管理接口不变（stop/status/device/event 命令继续通过 UDS 工作）

**Non-Goals:**
- 不提供 wrapper shell 脚本（用户自行使用 nohup）
- 不在 UDS status 响应中添加 PID 信息
- 不修改日志框架（SimLogger）的重定向能力

## Decisions

### 决策 1：删除 PID 文件机制，而非保留

**选择**: 完全删除 PID 文件（`--pid-file` 选项、`DaemonHelper` 的 write/delete/readPidFile 方法、AppConfig 中的 pidFile 字段）

**替代方案**: 保留 PID 文件写入，仅删 daemon fork 逻辑——保留 DaemonHelper 中的 writePidFile/deletePidFile 方法，StartCommand 启动后自写 PID

**理由**: PID 文件当前无任何控制逻辑消费它（stop 命令走 UDS），仅 shutdown hook 用于清理文件本身。保留它需要保留 pidFile 配置字段、DaemonHelper 的部分方法、shutdown hook 中的引用，删得不彻底。用户使用 nohup 时自行 `echo $! > pid` 即可。

### 决策 2：删除 log-file 选项

**选择**: 删除 `--log-file` 选项和 AppConfig 中的 logFile 字段

**替代方案**: 保留 log-file，让前台运行时也能重定向 stdout/stderr 到文件

**理由**: log-file 的实现是粗糙的 ProcessBuilder redirect，不与 SimLogger 集成。nohup 场景下 `> log 2>&1` 自然完成重定向，前台运行时 `tee` 也可用。应用内重定向属于 shell 职责，不应侵入应用代码。

## Risks / Trade-offs

- **[BREAKING] 用户脚本依赖 --daemon** → Migration: 改用 `nohup java -jar cpe-simulator.jar start --acs-url ... > log 2>&1 &`
- **[BREAKING] 用户脚本读取 PID 文件** → Migration: 改用 shell `$!` 变量：`nohup ... & echo $! > /tmp/cpe-sim.pid`
- **[低风险] shutdown hook 不再删 PID 文件** → 可接受，因为 PID 文件本身也被删除了