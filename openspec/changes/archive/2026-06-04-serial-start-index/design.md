## Context

当前模拟器序号生成逻辑：`DeviceManager.nextIndex` 以 `AtomicInteger(0)` 硬编码初始化，通过 `IdGenerator.serialNumber(prefix, index)` 生成 `prefix + %05d` 格式的序号。无法配置起始值，导致多实例并行或与已有数据衔接时序号冲突。

## Goals / Non-Goals

**Goals:**
- 支持通过 YAML 配置和 CLI 选项设定序号起始 index
- 默认值为 0，保持向后兼容
- 运行时 `device-add` 继续从当前 `nextIndex` 递增，不受 `serial-start` 影响

**Non-Goals:**
- 不实现重启续跑（持久化 index），这需要独立的 state 持久化机制
- 不改变 `%05d` 格式或序号位数策略
- 不影响 XMPP 用户名生成逻辑

## Decisions

**D1: 配置项命名为 `serial-start`**
- 与已有 `serial-prefix` 对称，语义清晰
- 替代方案 `serial-start-index` 冗余，`start-index` 太泛

**D2: `nextIndex` 初始化使用 `config.getSerialStart()`**
- 最小改动：仅修改 `DeviceManager` 构造函数中 `AtomicInteger` 的初始值
- `addDevices` 等运行时操作继续递增，无需额外逻辑

**D3: `IdGenerator.serialNumber()` 不变**
- `%05d` 超过 99999 时 Java 自动扩展位数，无需特殊处理
- 用户若需固定位数可自行控制起始值范围

## Risks / Trade-offs

- [序号位数不一致] → 若起始值 > 99999，序号将变成 6+ 位。ACS 端如依赖固定长度序号可能有解析问题。 mitigation：文档提示建议起始值 + count < 100000
- [重启后序号重复] → 模拟器每次重启都从 `serial-start` 开始，若两次使用相同配置，序号会重复。 mitigation：用户可通过不同 `serial-start` 值区分批次，或未来增加持久化