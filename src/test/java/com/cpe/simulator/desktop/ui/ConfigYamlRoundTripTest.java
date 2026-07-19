package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the desktop config editor writes YAML that the CLI ConfigLoader
 * reads back with identical semantics (round-trip compatibility).
 */
class ConfigYamlRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void writeYamlRoundTripsThroughConfigLoader() throws Exception {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.setCount(5000);
        config.setAcsUrl("http://acs.example.com:7547/");
        config.setOui("A1B2C3");
        config.setProductClass("Router");
        config.setSerialPrefix("RTR");
        config.setSerialStart(100);
        config.getInformSchedules().getPeriodic().setInterval(600);
        config.getInformSchedules().getPeriodic().setInitialJitter(0.5);
        config.setMaxSessions(2000);
        config.setConnReqHost("192.168.1.10");
        config.setConnReqPort(30005);
        config.setConnReqUser("cruser");
        config.setConnReqPassword("crpass");
        config.setRebootDelay(8);
        config.setDownloadSuccessRate(0.5);
        config.setXmppOnly(true);
        config.getXmpp().setHost("xmpp.example.com");
        config.getXmpp().setPort(15222);
        config.getXmpp().setDomain("example.com");
        config.getXmpp().setTlsMode("STARTTLS");
        config.getXmpp().setSaslMechanism("SCRAM-SHA-256");
        config.getXmpp().setReconnectMaxDelay(900);
        config.getLogging().setLevel("DEBUG");
        config.getLogging().setDeviceFilter("dev-1,dev-2");

        File yaml = tempDir.resolve("application.yaml").toFile();
        ConfigView.writeYaml(config, yaml);

        AppConfig loaded = ConfigLoader.load(yaml.getAbsolutePath());
        assertEquals("nat", loaded.getMode());
        assertEquals(5000, loaded.getCount());
        assertEquals("http://acs.example.com:7547/", loaded.getAcsUrl());
        assertEquals("A1B2C3", loaded.getOui());
        assertEquals("Router", loaded.getProductClass());
        assertEquals("RTR", loaded.getSerialPrefix());
        assertEquals(100, loaded.getSerialStart());
        assertEquals(600, loaded.getInformSchedules().getPeriodic().getInterval());
        assertEquals(0.5, loaded.getInformSchedules().getPeriodic().getInitialJitter());
        assertEquals(2000, loaded.getMaxSessions());
        assertEquals("192.168.1.10", loaded.getConnReqHost());
        assertEquals(30005, loaded.getConnReqPort());
        assertEquals("cruser", loaded.getConnReqUser());
        assertEquals("crpass", loaded.getConnReqPassword());
        assertTrue(loaded.isConnReqAuthEnabled());
        assertEquals(8, loaded.getRebootDelay());
        assertEquals(0.5, loaded.getDownloadSuccessRate());
        assertTrue(loaded.isXmppOnly());
        assertEquals("xmpp.example.com", loaded.getXmpp().getHost());
        assertEquals(15222, loaded.getXmpp().getPort());
        assertEquals("example.com", loaded.getXmpp().getDomain());
        assertEquals(AppConfig.XmppConfig.TlsMode.STARTTLS, loaded.getXmpp().getTlsMode());
        assertEquals("SCRAM-SHA-256", loaded.getXmpp().getSaslMechanism());
        assertEquals(900, loaded.getXmpp().getReconnectMaxDelay());
        assertEquals("DEBUG", loaded.getLogging().getLevel());
        assertEquals("dev-1,dev-2", loaded.getLogging().getDeviceFilter());
    }

    @Test
    void defaultPortStaysAutoAfterRoundTrip() throws Exception {
        AppConfig config = new AppConfig();
        config.getXmpp().setTlsMode(AppConfig.XmppConfig.TlsMode.NONE);

        File yaml = tempDir.resolve("application.yaml").toFile();
        ConfigView.writeYaml(config, yaml);

        String content = java.nio.file.Files.readString(yaml.toPath());
        assertFalse(content.contains("\n  port:"), "自动端口不应写入 YAML");

        AppConfig loaded = ConfigLoader.load(yaml.getAbsolutePath());
        assertEquals(5222, loaded.getXmpp().getPort()); // 由 tls-mode 推导
    }

    @Test
    void nullConnReqAuthOmitted() throws Exception {
        AppConfig config = new AppConfig();
        File yaml = tempDir.resolve("application.yaml").toFile();
        ConfigView.writeYaml(config, yaml);

        String content = java.nio.file.Files.readString(yaml.toPath());
        assertFalse(content.contains("conn-req-user"), "null 字段不应写入 YAML");

        AppConfig loaded = ConfigLoader.load(yaml.getAbsolutePath());
        assertFalse(loaded.isConnReqAuthEnabled());
    }
}
