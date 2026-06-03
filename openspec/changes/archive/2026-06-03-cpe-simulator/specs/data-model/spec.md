## ADDED Requirements

### Requirement: YAML 数据模型加载
系统 SHALL 从 YAML 配置文件加载数据模型定义，构建参数树结构。支持内置的 TR-181 和 TR-098 默认模型，以及用户自定义模型文件。

#### Scenario: 加载内置 TR-181 模型
- **WHEN** 配置 `--model tr181`
- **THEN** 系统从内置的 `tr181-default.yaml` 加载参数定义，构建以 `Device.` 为根的参数树

#### Scenario: 加载内置 TR-098 模型
- **WHEN** 配置 `--model tr098`
- **THEN** 系统从内置的 `tr098-default.yaml` 加载参数定义，构建以 `InternetGatewayDevice.` 为根的参数树

#### Scenario: 加载自定义模型文件
- **WHEN** 配置 `--model ./conf/models/custom.yaml`
- **THEN** 系统从指定路径加载 YAML 文件，构建参数树

### Requirement: 参数树结构
系统 SHALL 以树形结构组织数据模型参数，支持层次化路径访问（如 `Device.DeviceInfo.Manufacturer`），支持多实例对象（如 `Device.WANDevice.1.WANConnectionDevice.2.`）。

#### Scenario: 层次化参数访问
- **WHEN** 通过路径 `Device.DeviceInfo.SoftwareVersion` 访问参数
- **THEN** 系统沿参数树逐级查找，返回目标参数节点的值

#### Scenario: 多实例对象访问
- **WHEN** 通过路径 `Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.IPAddress` 访问参数
- **THEN** 系统按实例编号逐级查找，返回对应实例的参数值

#### Scenario: 列举子节点
- **WHEN** 查询 `Device.` 路径下 NextLevel=true 的参数名
- **THEN** 系统返回 `Device.` 的直接子节点名称列表（如 `DeviceInfo.`、`ManagementServer.`、`WANDevice.`）

### Requirement: 参数类型支持
系统 SHALL 支持 TR-069 定义的标准参数类型：string、int、unsignedInt、boolean、dateTime、base64。

#### Scenario: 类型化参数读取
- **WHEN** 读取类型为 `xsd:boolean` 的参数
- **THEN** 系统返回布尔值，SOAP 序列化时使用正确的 XSD 类型标注

#### Scenario: 类型化参数写入
- **WHEN** ACS 设置类型为 `xsd:unsignedInt` 的参数值为 "300"
- **THEN** 系统验证值为合法无符号整数后存储，非法值返回 Fault 9007

### Requirement: 参数读写权限
系统 SHALL 为每个参数定义读写权限（只读/可读写），在 SetParameterValues 时校验权限。

#### Scenario: 只读参数拒绝写入
- **WHEN** ACS 尝试设置 `Device.DeviceInfo.SerialNumber`（只读参数）
- **THEN** 系统拒绝写入，返回 SOAP Fault 9008

#### Scenario: 可写参数允许写入
- **WHEN** ACS 设置 `Device.ManagementServer.PeriodicInformInterval`（可写参数）
- **THEN** 系统接受写入，更新参数值

### Requirement: 模板变量解析
系统 SHALL 支持数据模型中的模板变量，在 CPE 实例初始化时将变量替换为实例特有的值。

#### Scenario: UUID 模板变量
- **WHEN** 参数默认值包含 `${random.uuid}`
- **THEN** 系统为每个 CPE 实例生成唯一的 UUID 替换该变量

#### Scenario: 序列号模板变量
- **WHEN** 参数默认值包含 `${device.serial}`
- **THEN** 系统替换为该 CPE 实例的序列号（如 `SIM00001`）

#### Scenario: ACS URL 模板变量
- **WHEN** 参数默认值包含 `${acs.url}`
- **THEN** 系统替换为配置的 ACS URL

#### Scenario: IP 地址模板变量
- **WHEN** 参数默认值包含 `${random.ip}`
- **THEN** 系统为每个 CPE 实例生成唯一的模拟 IP 地址

### Requirement: 参数通知属性
系统 SHALL 为每个参数维护通知属性（Notification 类型：0=关闭、1=被动、2=主动）和访问控制列表（AccessList）。

#### Scenario: 主动通知参数
- **WHEN** 参数的 Notification 类型为 2（主动通知）且参数值发生变化
- **THEN** 系统触发 EventCode 为 "4 VALUE CHANGE" 的 Inform，包含变化的参数名和新值

#### Scenario: 修改参数通知属性
- **WHEN** ACS 通过 SetParameterAttributes 将某参数的 Notification 改为 2
- **THEN** 系统更新该参数的通知属性，后续值变化时触发主动通知

### Requirement: 参数默认值初始化
系统 SHALL 在 CPE 实例创建时，根据数据模型定义为所有参数设置默认值。

#### Scenario: 实例初始化
- **WHEN** 创建新的 CPE 实例
- **THEN** 系统根据数据模型定义，为所有参数设置默认值，模板变量替换为实例特有值

#### Scenario: FactoryReset 恢复默认值
- **WHEN** 执行 FactoryReset 操作
- **THEN** 系统将所有参数恢复为数据模型定义的默认值，丢弃所有运行时修改
