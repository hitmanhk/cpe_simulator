## Why

当前 daemon 模式通过 Java ProcessBuilder 实现自 fork，存在 PID 文件竞争条件（父进程先写错误 PID）、命令构建逻辑脆弱（getJarPath 依赖反射猜测路径）、`--foreground` 作为内部实现细节暴露给用户等问题。用户实际使用 nohup 后台执行更简单可靠，Java 内建的 daemon 化机制无必要保留。

## What Changes

- **BREAKING** 删除 `--daemon` CLI 选项，用户应使用 `nohup java -jar cpe-simulator.jar start ... &` 后台运行
- **BREAKING** 删除 `--foreground` CLI 选项（仅为 daemon 二次启动的内部标志）
- **BREAKING** 删除 `--pid-file` CLI 选项，PID 文件机制整体移除
- **BREAKING** 删除 `--log-file` CLI 选项，日志重定向由 shell 层面处理（nohup + redirect）
- 删除 `DaemonHelper.java` 整个文件
- 删除 `AppConfig` 中 `daemon`、`pidFile`、`logFile` 字段及对应 getter/setter
- 清理 `StartCommand` 中 daemon 分支逻辑、shutdown hook 中 PID 文件操作
- 清理相关测试代码

## Capabilities

### New Capabilities

无新能力引入。

### Modified Capabilities

- `cli-start`: 移除 daemon/foreground/pidFile/logFile 选项，简化启动流程为纯前台模式

## Impact

- `cli/StartCommand.java` — 删除 4 个选项字段、daemon 分支逻辑、PID 文件操作
- `cli/DaemonHelper.java` — 整个文件删除
- `common/AppConfig.java` — 删除 3 个字段及 getter/setter
- `cli/CliCommandTest.java` — 删除 daemon 断言、PID 文件断言、DaemonHelper 测试方法
- 后台运行方式从 `--daemon` 改为外部 `nohup`，属于 **BREAKING** 行为变更