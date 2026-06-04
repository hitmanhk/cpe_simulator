## MODIFIED Requirements

### Requirement: CLI start command options
StartCommand SHALL 只提供前台运行所需的 CLI 选项。daemon、foreground、pidFile、logFile 四个选项 SHALL 被移除。应用 SHALL 不包含任何自 fork 或进程管理的逻辑。

#### Scenario: Start without daemon option
- **WHEN** 用户执行 `java -jar cpe-simulator.jar start --acs-url http://acs:7547`
- **THEN** 应用在当前进程前台运行，无 fork，无 PID 文件写入

#### Scenario: Background run via nohup
- **WHEN** 用户执行 `nohup java -jar cpe-simulator.jar start --acs-url http://acs:7547 > log 2>&1 &`
- **THEN** 应用在后台运行，stdout/stderr 由 shell 重定向到 log 文件

#### Scenario: Daemon option rejected
- **WHEN** 用户执行 `java -jar cpe-simulator.jar start --daemon --acs-url ...`
- **THEN** picocli 报错未知选项 `--daemon`，拒绝执行

#### Scenario: Stop via UDS unchanged
- **WHEN** 用户执行 `cpe-sim stop`
- **THEN** StopCommand 通过 UDS socket 发送停机请求，不依赖 PID 文件

## REMOVED Requirements

### Requirement: Daemon mode self-fork
**Reason**: Java ProcessBuilder 自 fork 机制存在 PID 竞争条件和路径猜测脆弱性，nohup 是更可靠的替代方案
**Migration**: 使用 `nohup java -jar cpe-simulator.jar start ... > log 2>&1 &` 替代 `--daemon`

### Requirement: PID file management
**Reason**: PID 文件无控制逻辑消费（stop 命令走 UDS），仅为信息性文件，nohup 场景下由 shell 管理 PID
**Migration**: 使用 shell `$!` 变量：`nohup ... & echo $! > /tmp/cpe-sim.pid`

### Requirement: Log file redirect option
**Reason**: 应用内 stdout/stderr 重定向不与日志框架集成，属于 shell 职责
**Migration**: 使用 shell 重定向：`> log 2>&1` 或 `| tee log`