package com.cpe.simulator.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XmppTlsConfigTest {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Test
    void parsesTlsModesCaseInsensitively() throws Exception {
        AppConfig.XmppConfig config = mapper.readValue("tls-mode: starttls", AppConfig.XmppConfig.class);

        assertEquals(AppConfig.XmppConfig.TlsMode.STARTTLS, config.getTlsMode());
        assertTrue(config.isStartTls());
        assertFalse(config.isDirectTls());
    }

    @Test
    void mapsLegacyTlsFlagToDirectTls() throws Exception {
        AppConfig.XmppConfig config = mapper.readValue("tls: true", AppConfig.XmppConfig.class);

        assertEquals(AppConfig.XmppConfig.TlsMode.DIRECT, config.getTlsMode());
        assertTrue(config.isDirectTls());
    }

    @Test
    void defaultsToNoTls() {
        AppConfig.XmppConfig config = new AppConfig.XmppConfig();
        assertEquals(AppConfig.XmppConfig.TlsMode.NONE, config.getTlsMode());
        assertEquals("SCRAM-SHA-1", config.getSaslMechanism());
    }

    @Test
    void legacySetterOverridesDefaultModeForCliCompatibility() {
        AppConfig.XmppConfig config = new AppConfig.XmppConfig();
        config.setTlsMode(AppConfig.XmppConfig.TlsMode.NONE);

        config.setTls(true);

        assertEquals(AppConfig.XmppConfig.TlsMode.DIRECT, config.getTlsMode());
    }
}
