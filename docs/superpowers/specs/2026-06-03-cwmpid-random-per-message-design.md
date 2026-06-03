# cwmpId 随机化与每消息生成

## 背景

TR-069 协议规定每个 SOAP 消息的 `cwmp:ID` 应为唯一随机值。当前实现存在两个问题：

1. **值非随机**：使用 `AtomicLong` 计数器生成顺序值 `SIM-1`, `SIM-2`, ...
2. **粒度错误**：整个 session 共用一个 cwmpId，而非每条消息一个

## 目标

- cwmpId 改为 32 位随机十六进制字符串（UUID 去连字符）
- 每个 CPE 发起的 SOAP 消息生成独立的 cwmpId
- CPE 响应 ACS 请求时继续 echo ACS 的 cwmpId（已正确，保持不变）

## 改动详情

### 1. `IdGenerator.cwmpId()`

**文件**: `src/main/java/com/cpe/simulator/common/IdGenerator.java`

- 移除 `private static final AtomicLong CWMP_ID_COUNTER`
- 实现改为 `UUID.randomUUID().toString().replace("-", "")`

### 2. `CwmpSession`

**文件**: `src/main/java/com/cpe/simulator/cwmp/CwmpSession.java`

移除 cwmpId 相关的所有内容：
- 字段 `private String cwmpId`
- `startSession()` 中的 `this.cwmpId = IdGenerator.cwmpId()`
- `reset()` 中的 `this.cwmpId = null`
- `getCwmpId()` 方法
- `setCwmpId(String)` 方法

### 3. `InformHandler.buildInform()`

**文件**: `src/main/java/com/cpe/simulator/cwmp/InformHandler.java`

将：
```java
String cwmpId = device.getSession().getCwmpId();
if (cwmpId == null) {
    cwmpId = IdGenerator.cwmpId();
}
```

改为：
```java
String cwmpId = IdGenerator.cwmpId();
```

### 4. `CpeDevice`

**文件**: `src/main/java/com/cpe/simulator/engine/CpeDevice.java`

#### `sendEmptyRequest()` (line 217)

将：
```java
String emptyXml = soapBuilder.buildEmptyRequest(session.getCwmpId());
```

改为：
```java
String emptyXml = soapBuilder.buildEmptyRequest(IdGenerator.cwmpId());
```

#### `processAcsRequests()` fault 响应 (line 193)

将：
```java
String faultXml = soapBuilder.buildFault(9000, "Method not supported: " + methodName, session.getCwmpId());
```

改为：
```java
String faultXml = soapBuilder.buildFault(9000, "Method not supported: " + methodName, request.getCwmpId());
```

### 5. 测试

无需改动。测试中使用的 `SIM-1` 等值是作为任意字符串传入的，不依赖生成逻辑。

## 不变的部分

- `SoapBuilder` 所有方法签名不变（已接收 cwmpId 参数）
- CPE 响应 ACS 请求时 echo ACS 的 cwmpId（各 Handler 中的 `response.setCwmpId(request.getCwmpId())`）
- `SoapParser` 解析逻辑

## 验证

- `mvn test` 通过
- 检查生成的 cwmpId 为 32 位十六进制字符串
