package com.cpe.acsit.harness;

import com.cpe.acsit.acs.AcsServer;
import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.engine.SimulationEngine;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Boots a real SimulationEngine against the scriptable AcsServer on random
 * loopback ports. Tests customize the AppConfig via {@link #configure} before
 * {@link #start()}.
 */
public class SimHarness implements AutoCloseable {

    public static final String MODEL_TR181 = "target/test-classes/models/tr181.yaml";
    public static final String MODEL_TR098 = "target/test-classes/models/tr098.yaml";

    public final AcsServer acs;
    public final AppConfig config = new AppConfig();
    private SimulationEngine engine;

    public SimHarness() {
        this.acs = new AcsServer();
        // 测试默认值：禁掉周期调度（各测试按需开启），缩短模拟延迟，下载恒成功
        config.setCount(1);
        config.getInformSchedules().getBootstrap().setInterval(0);
        config.getInformSchedules().getBoot().setInterval(0);
        config.getInformSchedules().getPeriodic().setInterval(0);
        config.setRebootDelay(1);
        config.setDownloadDelay(1);
        config.setDownloadSuccessRate(1.0);
        config.setModel(MODEL_TR181);
    }

    /** Hook to tweak config before engine start. */
    public SimHarness configure(java.util.function.Consumer<AppConfig> customizer) {
        customizer.accept(config);
        return this;
    }

    public SimHarness start() throws Exception {
        acs.start();
        config.setAcsUrl(acs.getUrl());
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(freePort());
        engine = new SimulationEngine(config);
        engine.start();
        return this;
    }

    public SimulationEngine engine() {
        return engine;
    }

    @Override
    public void close() {
        try {
            if (engine != null) {
                engine.stop();
            }
        } finally {
            acs.stop();
        }
    }

    // ---------------- await helpers ----------------

    public static void awaitTrue(BooleanSupplier condition, Duration timeout, String description) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + description);
            }
        }
        throw new AssertionError("Timed out (" + timeout.toSeconds() + "s) waiting for: " + description);
    }

    public static int freePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
