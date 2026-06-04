# BOOT Inform 阶梯式分批启动设计

## 问题

10000 设备批量启动时出现 `HTTP/1.1 header parser received no bytes` 错误。3 个根因：

1. **HTTP/2 协议升级**：JDK HttpClient 默认尝试 HTTP/2（ALPN），GenieACS 只支持 HTTP/1.1，连接被 ACS 立即关闭
2. **无 stagger 机制**：`SimulationEngine.start()` 中 for 循环瞬间触发 10000 设备的 BOOT Inform，形成 1000 并发 HTTP 请求脉冲
3. **Executor 泄漏**：`BatchConnector` 每批创建 `VirtualThreadPerTaskExecutor` 不关闭，10 批泄漏 10 个 executor

## 修复方案

### 修复 1：HttpClient 强制 HTTP/1.1

文件：`CwmpClient.java:29-32`

```java
// 修改前
HttpClient.newBuilder()
    .executor(Executors.newVirtualThreadPerTaskExecutor())
    .connectTimeout(Duration.ofSeconds(30))
    .build();

// 修改后
HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .executor(Executors.newVirtualThreadPerTaskExecutor())
    .connectTimeout(Duration.ofSeconds(30))
    .build();
```

原因：GenieACS 只支持 HTTP/1.1，HTTP/2 协议升级请求被 ACS 拒绝并关闭连接。

### 修复 2：新建 BootLauncher 阶梯式分批触发

新建 `com.cpe.simulator.engine.BootLauncher` 类。

核心逻辑：
- 将设备列表按 `boot-batch-size` 分批
- 每批内每个设备加随机延迟（0 ~ `boot-batch-delay * boot-jitter` ms）后触发 `triggerInform("1 BOOT")`
- 批间有 `boot-batch-delay` ms 固定间隔

时间线示意（10000 设备，batch-size=100，batch-delay=1000ms，jitter=0.5）：

```
t=0s     : 设备 1-100   开始 BOOT（每设备加 0~500ms 随机延迟）
t=1s     : 设备 101-200 开始 BOOT（每设备加 0~500ms 随机延迟）
t=2s     : 设备 201-300 开始 BOOT
...
t=99s    : 设备 9901-10000 开始 BOOT
总耗时约 100s
```

BootLauncher 使用 `ScheduledExecutorService.schedule()` 为每批内每个设备调度随机延迟后执行 `triggerInform`，避免瞬间冲击。

SimulationEngine 改动：

```java
// 修改前
for (CpeDevice device : devices) {
    device.triggerInform("1 BOOT");
}

// 修改后
BootLauncher bootLauncher = new BootLauncher(config);
bootLauncher.launchBoot(devices).join();
```

新增配置参数（AppConfig）：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `boot-batch-size` | 100 | 每批触发多少设备的 BOOT Inform |
| `boot-batch-delay` | 1000(ms) | 批次之间的固定延迟 |
| `boot-jitter` | 0.5 | 每批内设备的随机抖动系数，实际延迟范围 0 ~ batchDelay * jitter |

CLI 参数映射：`--boot-batch-size 200 --boot-batch-delay 500 --boot-jitter 0.3`

### 修复 3：BatchConnector 简化 + Executor 泄漏消除

文件：`BatchConnector.java`

`device.connect()` 只做状态切换（OFFLINE → ONLINE），无 HTTP 请求，极轻量。不需要并发执行，顺序遍历即可。

```java
// 修改前：每批创建新 executor（泄漏！）
var executor = Executors.newVirtualThreadPerTaskExecutor();
List<CompletableFuture<Void>> futures = batch.stream()
    .map(device -> CompletableFuture.runAsync(() -> device.connect(), executor))
    .collect(Collectors.toList());
CompletableFuture.allOf(futures.toArray(...)).join();

// 修改后：顺序执行
for (int j = i; j < end; j++) {
    devices.get(j).connect();
}
```

去掉 CompletableFuture、VirtualThreadPerTaskExecutor、Collectors 等多余的并发构造。

## 设计约束

- BootLauncher 是一次性的启动组件，只在 `SimulationEngine.start()` 中使用
- 周期性 Inform 的 jitter 机制（InformScheduler）保持不变，不受此改动影响
- Semaphore（max-sessions）仍然作为全局并发会话数上限，与 BootLauncher 的 stagger 互补而非替代
- BootLauncher 返回 `CompletableFuture<Void>`，由 SimulationEngine `.join()` 阻塞等待全部设备完成 BOOT Inform

## 测试策略

- `SoapBuilderTest` 等现有单设备测试不受影响
- 新增 `BootLauncherTest`：验证分批逻辑、jitter 范围、CompletableFuture 完成信号
- 集成测试（`PublicModeIntegrationTest`）中设备数量较小（默认 100），boot-batch-size=100 时行为与原来一致（1 批就完成），不影响现有测试