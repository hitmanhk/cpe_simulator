## Why

模拟器序号（serialNumber）始终从 index 0 开始生成，无法配置起始值。在多实例并行模拟、与已有 ACS 数据衔接等场景下，序号会冲突或覆盖。需要支持可配置的起始 index，让用户灵活控制序号编号区间。

## What Changes

- 新增 `serial-start` 配置项（YAML / CLI），控制序号起始数字，默认值 0
- `DeviceManager.nextIndex` 初始化值从硬编码 0 改为读取 `serial-start` 配置
- CLI `StartCommand` 新增 `--serial-start` 选项

## Capabilities

### New Capabilities
- `serial-start`: 序号起始 index 可配置，支持 YAML 配置和 CLI 选项传入

### Modified Capabilities
（无已有 specs，无需修改）

## Impact

- `AppConfig.java` — 新增 `serialStart` 字段及 getter/setter
- `DeviceManager.java` — `nextIndex` 初始化值改为 `config.getSerialStart()`
- `StartCommand.java` — 新增 `--serial-start` CLI 选项及 `applyCliOverrides` 映射
- YAML 配置文件 — 可选新增 `serial-start` 键
- `IdGenerator.java` — 无需改动（`%05d` 自动扩展超限位数）