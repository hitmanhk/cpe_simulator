## ADDED Requirements

### Requirement: Serial start index configuration
系统 SHALL 支持通过 YAML 配置键 `serial-start` 和 CLI 选项 `--serial-start` 设定序号起始数字。默认值 SHALL 为 0，保持向后兼容。

#### Scenario: Default serial start
- **WHEN** 用户未指定 `serial-start` 配置或 CLI 选项
- **THEN** 序号从 0 开始生成（SIM00000、SIM00001...），行为与改动前一致

#### Scenario: Custom serial start via YAML
- **WHEN** YAML 配置中设置 `serial-start: 1000`
- **THEN** 序号从 1000 开始生成（SIM01000、SIM01001...）

#### Scenario: Custom serial start via CLI
- **WHEN** CLI 传入 `--serial-start 500`
- **THEN** 序号从 500 开始生成（SIM00500、SIM00501...），覆盖 YAML 配置值

#### Scenario: CLI override YAML
- **WHEN** YAML 配置 `serial-start: 1000` 且 CLI 传入 `--serial-start 500`
- **THEN** CLI 值覆盖 YAML 值，序号从 500 开始

### Requirement: Runtime device addition unaffected
运行时通过 UDS `device-add` 动态添加设备 SHALL 从当前 `nextIndex` 继续递增，不受 `serial-start` 重新初始化影响。

#### Scenario: Add devices after initial start
- **WHEN** 模拟器以 `--serial-start 100 --count 50` 启动后，通过 UDS 添加 10 台设备
- **THEN** 初始设备序号为 SIM00100~SIM00149，新增设备序号为 SIM00150~SIM00159