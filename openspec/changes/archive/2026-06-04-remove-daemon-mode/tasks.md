## 1. 删除 DaemonHelper

- [x] 1.1 删除 `src/main/java/com/cpe/simulator/cli/DaemonHelper.java` 文件

## 2. 简化 StartCommand

- [x] 2.1 删除 `--daemon`、`--foreground`、`--pid-file`、`--log-file` 四个 @Option 字段
- [x] 2.2 删除 `if (daemon && !foreground)` 分支逻辑及其 return
- [x] 2.3 删除 shutdown hook 中的 `DaemonHelper.deletePidFile(pidFile)` 调用
- [x] 2.4 删除启动后的 `DaemonHelper.writePidFile(pidFile)` 调用
- [x] 2.5 删除 `applyCliOverrides` 中的 `config.setDaemon(daemon)`、`config.setPidFile(pidFile)`、`if (logFile != null) config.setLogFile(logFile)` 三行

## 3. 清理 AppConfig

- [x] 3.1 删除 `daemon` 字段（@JsonProperty + private + getter/setter 共 3 行）
- [x] 3.2 删除 `pidFile` 字段（@JsonProperty + private + getter/setter 共 3 行）
- [x] 3.3 删除 `logFile` 字段（@JsonProperty + private + getter/setter 共 3 行）

## 4. 清理测试

- [x] 4.1 删除 CliCommandTest 中的 `assertFalse(cmd.daemon)` 断言
- [x] 4.2 删除 CliCommandTest 中的 `assertEquals("/tmp/cpe-sim.pid", cmd.pidFile)` 断言
- [x] 4.3 删除 CliCommandTest 中的 `testDaemonHelperPidOperations` 整个测试方法

## 5. 验证

- [x] 5.1 运行 `mvn test` 确认所有测试通过
- [x] 5.2 运行 `mvn clean package` 确认编译打包成功