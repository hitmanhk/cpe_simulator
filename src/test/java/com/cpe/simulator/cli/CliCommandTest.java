package com.cpe.simulator.cli;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class CliCommandTest {

    @Test
    void testStartCommandNatModeMissingXmppHost() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.xmppHost = null;
        cmd.xmppDomain = "test.com";
        cmd.xmppPassword = "secret";

        String error = cmd.validateNatMode();
        assertNotNull(error);
        assertTrue(error.contains("xmpp-host"));
    }

    @Test
    void testStartCommandNatModeMissingXmppDomain() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.xmppHost = "xmpp.test.com";
        cmd.xmppDomain = null;
        cmd.xmppPassword = "secret";

        String error = cmd.validateNatMode();
        assertNotNull(error);
        assertTrue(error.contains("xmpp-domain"));
    }

    @Test
    void testStartCommandNatModeMissingXmppPassword() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.xmppHost = "xmpp.test.com";
        cmd.xmppDomain = "test.com";
        cmd.xmppPassword = null;

        String error = cmd.validateNatMode();
        assertNotNull(error);
        assertTrue(error.contains("xmpp-password"));
    }

    @Test
    void testStartCommandNatModeAllPresent() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.xmppHost = "xmpp.test.com";
        cmd.xmppDomain = "test.com";
        cmd.xmppPassword = "secret";

        String error = cmd.validateNatMode();
        assertNull(error);
    }

    @Test
    void testStartCommandPublicModeNoValidation() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "public";

        String error = cmd.validateNatMode();
        assertNull(error);
    }

    @Test
    void testStartCommandPublicModeDefaults() {
        StartCommand cmd = new StartCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs("--acs-url", "http://localhost:7547");
        assertEquals("public", cmd.mode);
        assertEquals(100, cmd.count);
        assertEquals("tr181", cmd.model);
        assertEquals("000000", cmd.oui);
        assertEquals("SimCPE", cmd.productClass);
        assertEquals("SIM", cmd.serialPrefix);
        assertEquals(300, cmd.informInterval);
        assertEquals(0.3, cmd.informJitter, 0.001);
        assertEquals(1000, cmd.maxSessions);
        assertEquals(1000, cmd.connectBatch);
        assertEquals(200, cmd.connectBatchDelay);
        assertEquals("0.0.0.0", cmd.connReqHost);
        assertEquals(7547, cmd.connReqPort);
        assertNull(cmd.xmppPort);
        assertEquals("cpe", cmd.xmppUserPrefix);
        assertEquals("sim", cmd.xmppResource);
        assertNull(cmd.xmppTlsMode);
        assertEquals(60, cmd.xmppHandshakeTimeout);
        assertEquals(5, cmd.rebootDelay);
        assertEquals(10, cmd.downloadDelay);
        assertEquals(0.99, cmd.downloadSuccessRate, 0.001);
    }

    @Test
    void testStartCommandCustomValues() {
        StartCommand cmd = new StartCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs(
            "--acs-url", "http://acs.example.com:8080",
            "--count", "500",
            "--mode", "public",
            "--oui", "AABBCC",
            "--product-class", "TestDevice",
            "--serial-prefix", "TST",
            "--periodic-inform-interval", "600",
            "--max-sessions", "500"
        );

        assertEquals("http://acs.example.com:8080", cmd.acsUrl);
        assertEquals(500, cmd.count);
        assertEquals("public", cmd.mode);
        assertEquals("AABBCC", cmd.oui);
        assertEquals("TestDevice", cmd.productClass);
        assertEquals("TST", cmd.serialPrefix);
        assertEquals(600, cmd.informInterval);
        assertEquals(500, cmd.maxSessions);
    }

    @Test
    void testXmppTlsAndSaslOptions() {
        StartCommand cmd = new StartCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs("--acs-url", "http://localhost:7547",
            "--xmpp-tls-mode", "STARTTLS",
            "--xmpp-sasl-mechanism", "SCRAM-SHA-256",
            "--xmpp-handshake-timeout", "45");

        assertEquals("STARTTLS", cmd.xmppTlsMode);
        assertEquals("SCRAM-SHA-256", cmd.xmppSaslMechanism);
        assertEquals(45, cmd.xmppHandshakeTimeout);
    }

    @Test
    void connectionRequestHostOverridesYaml() {
        StartCommand cmd = new StartCommand();
        CommandLine cl = new CommandLine(cmd);
        cl.parseArgs("--conn-req-host", "192.0.2.11");

        AppConfig config = new AppConfig();
        cmd.applyCliOverrides(config);

        assertEquals("192.0.2.11", config.getConnReqHost());
    }

    @Test
    void explicitCliValuesOverrideYamlWithoutOverwritingOtherValues() {
        StartCommand cmd = new StartCommand();
        CommandLine cl = new CommandLine(cmd);
        cl.parseArgs(
            "--acs-url", "http://cli.example.com:7547/",
            "--bootstrap-inform-interval", "7200",
            "--periodic-inform-initial-jitter", "0.9"
        );

        AppConfig config = new AppConfig();
        config.setCount(777);
        config.getInformSchedules().getPeriodic().setInterval(120);
        config.getInformSchedules().getBootstrap().setInterval(0);

        cmd.applyCliOverrides(config);

        assertEquals("http://cli.example.com:7547/", config.getAcsUrl());
        assertEquals(777, config.getCount());
        assertEquals(120, config.getInformSchedules().getPeriodic().getInterval());
        assertEquals(0.9, config.getInformSchedules().getPeriodic().getInitialJitter(), 0.001);
        assertEquals(7200, config.getInformSchedules().getBootstrap().getInterval());
    }

    @Test
    void testVersionCommandOutput() {
        VersionCommand cmd = new VersionCommand();

        String[] version = cmd.getVersion();
        assertNotNull(version);
        assertEquals(3, version.length);
        assertTrue(version[0].contains("CPE Simulator"));
        assertTrue(version[1].contains("Build time"));
        assertTrue(version[2].contains("JDK"));
    }

    @Test
    void testVersionProviderInterface() throws Exception {
        VersionCommand cmd = new VersionCommand();
        String[] version = cmd.getVersion();
        assertNotNull(version);
        assertTrue(version.length >= 1);
        assertTrue(version[0].startsWith("CPE Simulator v"));
    }

    @Test
    void testMainCommandSubcommands() {
        MainCommand main = new MainCommand();
        CommandLine cl = new CommandLine(main);

        assertTrue(cl.getSubcommands().containsKey("start"));
        assertTrue(cl.getSubcommands().containsKey("stop"));
        assertTrue(cl.getSubcommands().containsKey("status"));
        assertTrue(cl.getSubcommands().containsKey("device"));
        assertTrue(cl.getSubcommands().containsKey("event"));
        assertTrue(cl.getSubcommands().containsKey("version"));
    }

    @Test
    void testDeviceCommandSubcommands() {
        DeviceCommand cmd = new DeviceCommand();
        CommandLine cl = new CommandLine(cmd);

        assertTrue(cl.getSubcommands().containsKey("list"));
        assertTrue(cl.getSubcommands().containsKey("get"));
        assertTrue(cl.getSubcommands().containsKey("add"));
        assertTrue(cl.getSubcommands().containsKey("remove"));
        assertTrue(cl.getSubcommands().containsKey("inform"));
    }

    @Test
    void testEventCommandSubcommands() {
        EventCommand cmd = new EventCommand();
        CommandLine cl = new CommandLine(cmd);

        assertTrue(cl.getSubcommands().containsKey("reboot"));
        assertTrue(cl.getSubcommands().containsKey("factory-reset"));
        assertTrue(cl.getSubcommands().containsKey("value-change"));
    }

    @Test
    void testStopCommandDefaults() {
        StopCommand cmd = new StopCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs();
        assertFalse(cmd.force);
        assertEquals(30, cmd.timeout);
    }

    @Test
    void testStopCommandWithOptions() {
        StopCommand cmd = new StopCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs("--force", "--timeout", "60");
        assertTrue(cmd.force);
        assertEquals(60, cmd.timeout);
    }

    @Test
    void testStatusCommandDefaults() {
        StatusCommand cmd = new StatusCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs();
        assertFalse(cmd.json);
    }

    @Test
    void testStatusCommandJsonFlag() {
        StatusCommand cmd = new StatusCommand();
        CommandLine cl = new CommandLine(cmd);

        cl.parseArgs("--json");
        assertTrue(cmd.json);
    }

    @Test
    void testProgressBarUpdate() {
        ProgressBar bar = new ProgressBar();
        assertDoesNotThrow(() -> bar.update("Testing", 50, 100));
        assertDoesNotThrow(() -> bar.update("Testing", 100, 100));
    }

    @Test
    void testProgressBarComplete() {
        ProgressBar bar = new ProgressBar();
        assertDoesNotThrow(() -> bar.complete("Done!"));
    }

    }
