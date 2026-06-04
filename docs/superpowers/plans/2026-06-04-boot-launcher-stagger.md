# BOOT Inform 阶梯式分批启动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 10000-device batch startup error by forcing HTTP/1.1, adding staggered boot launch, and eliminating executor leaks.

**Architecture:** Three independent fixes: (1) add `.version(HTTP_1_1)` to HttpClient, (2) new BootLauncher class with batch+jitter stagger replacing the instant for-loop, (3) simplify BatchConnector to sequential loop removing executor leak.

**Tech Stack:** Java 21, JDK HttpClient, ScheduledExecutorService, virtual threads, picocli CLI

---

### Task 1: HttpClient HTTP/1.1 强制设置

**Files:**
- Modify: `src/main/java/com/cpe/simulator/cwmp/CwmpClient.java:29-32`
- Test: `src/test/java/com/cpe/simulator/cwmp/CwmpClientAuthTest.java`

- [ ] **Step 1: 修改 CwmpClient 构造函数添加 HTTP/1.1 版本**

在 `CwmpClient.java` 第 29 行 `HttpClient.newBuilder()` 之后添加 `.version(HttpClient.Version.HTTP_1_1)`：

```java
public CwmpClient(AppConfig config) {
    this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(30))
            .build();
```

- [ ] **Step 2: 运行现有 CwmpClient 测试确认无破坏**

Run: `mvn test -Dtest=CwmpClientAuthTest -pl .`
Expected: 所有测试 PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/cwmp/CwmpClient.java
git commit -m "fix: force HTTP/1.1 in CwmpClient HttpClient to avoid HTTP/2 upgrade rejection by ACS"
```

---

### Task 2: AppConfig 新增 boot 配置参数

**Files:**
- Modify: `src/main/java/com/cpe/simulator/common/AppConfig.java`
- Modify: `src/main/java/com/cpe/simulator/cli/StartCommand.java`

- [ ] **Step 1: 在 AppConfig 添加 3 个 boot 配置字段及 getter/setter**

在 `AppConfig.java` 的 `connect-batch-delay` 字段（第 49 行）之后添加：

```java
@JsonProperty("boot-batch-size")
private int bootBatchSize = 100;

@JsonProperty("boot-batch-delay")
private int bootBatchDelay = 1000;

@JsonProperty("boot-jitter")
private double bootJitter = 0.5;
```

在 `getConnectBatchDelay` setter（第 120 行）之后添加 getter/setter：

```java
public int getBootBatchSize() { return bootBatchSize; }
public void setBootBatchSize(int bootBatchSize) { this.bootBatchSize = bootBatchSize; }
public int getBootBatchDelay() { return bootBatchDelay; }
public void setBootBatchDelay(int bootBatchDelay) { this.bootBatchDelay = bootBatchDelay; }
public double getBootJitter() { return bootJitter; }
public void setBootJitter(double bootJitter) { this.bootJitter = bootJitter; }
```

- [ ] **Step 2: 在 StartCommand 添加 3 个 CLI 选项及 applyCliOverrides 映射**

在 `StartCommand.java` 的 `connect-batch-delay` 选项（第 55 行）之后添加：

```java
@Option(names = {"--boot-batch-size"}, description = "BOOT inform batch size", defaultValue = "100")
int bootBatchSize;

@Option(names = {"--boot-batch-delay"}, description = "BOOT batch delay in ms", defaultValue = "1000")
int bootBatchDelay;

@Option(names = {"--boot-jitter"}, description = "BOOT inform jitter ratio (0.0-1.0)", defaultValue = "0.5")
double bootJitter;
```

在 `applyCliOverrides` 方法（第 241 行 `setConnectBatchDelay` 之后）添加：

```java
config.setBootBatchSize(bootBatchSize);
config.setBootBatchDelay(bootBatchDelay);
config.setBootJitter(bootJitter);
```

- [ ] **Step 3: 运行 StartupModeTest 确认无破坏**

Run: `mvn test -Dtest=StartupModeTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cpe/simulator/common/AppConfig.java src/main/java/com/cpe/simulator/cli/StartCommand.java
git commit -m "feat: add boot-batch-size, boot-batch-delay, boot-jitter config and CLI options"
```

---

### Task 3: BootLauncher 类实现 + 测试

**Files:**
- Create: `src/main/java/com/cpe/simulator/engine/BootLauncher.java`
- Create: `src/test/java/com/cpe/simulator/engine/BootLauncherTest.java`

- [ ] **Step 1: 创建 BootLauncher 类**

```java
package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.SimLogger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BootLauncher {

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;

    public BootLauncher(AppConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Thread.ofVirtual().factory()
        );
    }

    public CompletableFuture<Void> launchBoot(List<CpeDevice> devices) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        int batchSize = config.getBootBatchSize();
        int batchDelay = config.getBootBatchDelay();
        double jitter = config.getBootJitter();
        long jitterMaxMs = (long) (batchDelay * jitter);

        scheduler.execute(() -> {
            try {
                for (int i = 0; i < devices.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, devices.size());

                    for (int j = i; j < end; j++) {
                        CpeDevice device = devices.get(j);
                        long deviceDelay = jitterMaxMs > 0
                            ? (long) (Math.random() * jitterMaxMs)
                            : 0;

                        if (deviceDelay > 0) {
                            scheduler.schedule(() -> {
                                try {
                                    device.triggerInform("1 BOOT");
                                } catch (Exception e) {
                                    SimLogger.error("BOOT inform failed for device " + device.getDeviceId(), e);
                                }
                            }, deviceDelay, TimeUnit.MILLISECONDS);
                        } else {
                            try {
                                device.triggerInform("1 BOOT");
                            } catch (Exception e) {
                                SimLogger.error("BOOT inform failed for device " + device.getDeviceId(), e);
                            }
                        }
                    }

                    if (end < devices.size()) {
                        Thread.sleep(batchDelay);
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 2: 创建 BootLauncherTest**

```java
package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BootLauncherTest {

    @Test
    void testLaunchBootCompletesForSmallBatch() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(10);
        config.setBootBatchDelay(100);
        config.setBootJitter(0.0);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(20);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testLaunchBootWithJitterCompletes() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(10);
        config.setBootBatchDelay(200);
        config.setBootJitter(0.5);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(30);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testSingleBatchNoDelay() {
        AppConfig config = new AppConfig();
        config.setBootBatchSize(100);
        config.setBootBatchDelay(1000);
        config.setBootJitter(0.0);
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");

        BootLauncher launcher = new BootLauncher(config);
        List<CpeDevice> devices = createMockDevices(50);

        launcher.launchBoot(devices).join();

        launcher.shutdown();
    }

    @Test
    void testDefaultConfigValues() {
        AppConfig config = new AppConfig();
        assertEquals(100, config.getBootBatchSize());
        assertEquals(1000, config.getBootBatchDelay());
        assertEquals(0.5, config.getBootJitter());
    }

    private List<CpeDevice> createMockDevices(int count) {
        List<CpeDevice> devices = new ArrayList<>();
        com.cpe.simulator.model.DataModel dm = new com.cpe.simulator.model.DataModel();
        dm.saveDefaults();

        Semaphore semaphore = new Semaphore(100);
        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        AppConfig config = new AppConfig();
        config.setMaxSessions(100);
        config.setOui("000000");
        config.setProductClass("SimCPE");
        config.setSerialPrefix("SIM");
        config.setAcsUrl("http://localhost:7547/");

        for (int i = 0; i < count; i++) {
            String sn = "SIM" + String.format("%05d", i + 1);
            String deviceId = "000000-SimCPE-" + sn;
            com.cpe.simulator.model.DataModel deviceModel = dm.deepCopy();

            CpeDevice device = new CpeDevice(
                deviceId, sn, "000000", "SimCPE",
                deviceModel, config, null, scheduler, semaphore
            );
            device.initialize();
            device.connect();
            devices.add(device);
        }

        return devices;
    }
}
```

注意：`createMockDevices` 创建的设备 `cwmpClient=null`，因此 `triggerInform` 会在 `runInformSession` 中因 `cwmpClient` 为 null 抛异常，但 `triggerInform` 内部 catch 会处理。这只是验证 BootLauncher 的分批调度逻辑能正常完成，不验证实际 HTTP 请求。

- [ ] **Step 3: 运行 BootLauncherTest**

Run: `mvn test -Dtest=BootLauncherTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cpe/simulator/engine/BootLauncher.java src/test/java/com/cpe/simulator/engine/BootLauncherTest.java
git commit -m "feat: add BootLauncher with batch+jitter stagger for BOOT inform"
```

---

### Task 4: SimulationEngine 集成 BootLauncher

**Files:**
- Modify: `src/main/java/com/cpe/simulator/engine/SimulationEngine.java:36-48,130-139,153-194`

- [ ] **Step 1: 在 SimulationEngine 中添加 BootLauncher 字段和集成代码**

1. 添加字段（在 `batchConnector` 字段第 43 行之后）：

```java
private BootLauncher bootLauncher;
```

2. 修改 `start()` 方法中的 BOOT Inform 触发逻辑（替换第 133-140 行）：

```java
if (!config.isXmppOnly()) {
    batchConnector = new BatchConnector(config);
    batchConnector.connectBatch(devices).join();

    bootLauncher = new BootLauncher(config);
    bootLauncher.launchBoot(devices).join();
}
```

3. 修改 `stop()` 方法（在 `batchConnector.shutdown()` 第 186 行之后添加）：

```java
if (bootLauncher != null) {
    bootLauncher.shutdown();
}
```

4. 修改 `stopForced()` 方法（在 `batchConnector.shutdown()` 第 213 行之后添加）：

```java
if (bootLauncher != null) {
    bootLauncher.shutdown();
}
```

- [ ] **Step 2: 运行 SimulationEngineTest 确认无破坏**

Run: `mvn test -Dtest=SimulationEngineTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/engine/SimulationEngine.java
git commit -m "feat: integrate BootLauncher into SimulationEngine for staggered BOOT inform"
```

---

### Task 5: BatchConnector 简化 + Executor 泄漏消除

**Files:**
- Modify: `src/main/java/com/cpe/simulator/engine/BatchConnector.java`

- [ ] **Step 1: 简化 BatchConnector，去掉 executor 泄漏**

将整个 `BatchConnector.java` 替换为简化版本：

```java
package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BatchConnector {

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;

    public BatchConnector(AppConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    }

    public CompletableFuture<Void> connectBatch(List<CpeDevice> devices) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        int batchSize = config.getConnectBatch();
        int batchDelay = config.getConnectBatchDelay();

        scheduler.execute(() -> {
            try {
                for (int i = 0; i < devices.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, devices.size());
                    for (int j = i; j < end; j++) {
                        devices.get(j).connect();
                    }
                    if (end < devices.size()) {
                        Thread.sleep(batchDelay);
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

关键变化：
- 去掉了 `Executors.newVirtualThreadPerTaskExecutor()` 每批创建+泄漏
- 去掉了 `CompletableFuture.runAsync` + `Collectors.toList()` + `allOf().join()`
- `device.connect()` 顺序执行（只改状态，极轻量）

- [ ] **Step 2: 运行现有测试确认无破坏**

Run: `mvn test -Dtest=SimulationEngineTest,DeviceManagerTest,StartupModeTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/engine/BatchConnector.java
git commit -m "fix: simplify BatchConnector to sequential loop, eliminate executor leak"
```

---

### Task 6: 全量测试验证

- [ ] **Step 1: 运行全量单元测试**

Run: `mvn test`
Expected: 所有测试 PASS

- [ ] **Step 2: 构建验证**

Run: `mvn clean package -DskipTests`
Expected: 成功生成 `target/cpe-simulator.jar`

- [ ] **Step 3: 最终 Commit（如有 lint/style 修正）**

仅当 Step 1/2 发现问题时才需要额外 commit。