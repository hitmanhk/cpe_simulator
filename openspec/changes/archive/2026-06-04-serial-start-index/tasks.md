## 1. 配置层

- [x] 1.1 在 `AppConfig.java` 新增 `serialStart` 字段（`@JsonProperty("serial-start")`，默认值 0）及 getter/setter
- [x] 1.2 在 `StartCommand.java` 新增 `--serial-start` CLI 选项（defaultValue = "0"）
- [x] 1.3 在 `StartCommand.applyCliOverrides()` 中添加 `config.setSerialStart(serialStart)` 映射

## 2. 核心逻辑

- [x] 2.1 修改 `DeviceManager.java` 构造函数，将 `nextIndex` 初始化从 `new AtomicInteger(0)` 改为 `new AtomicInteger(config.getSerialStart())`

## 3. 测试

- [x] 3.1 验证默认行为：不传 `--serial-start` 时序号从 0 开始（向后兼容）
- [x] 3.2 验证自定义起始值：传入 `--serial-start 100` 时序号从 SIM00100 开始
- [x] 3.3 验证运行时 addDevices 递增不受影响