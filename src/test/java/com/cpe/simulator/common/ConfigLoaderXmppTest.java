package com.cpe.simulator.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderXmppTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsTopLevelXmppTlsAndSaslConfiguration() throws Exception {
        Path configFile = tempDir.resolve("simulator.yaml");
        Files.writeString(configFile, """
            simulator:
              mode: nat
            xmpp:
              host: xmpp.example.com
              tls-mode: STARTTLS
              sasl-mechanism: SCRAM-SHA-256
              handshake-timeout: 45
              reconnect-initial-delay: 120
              reconnect-max-delay: 900
            """);

        AppConfig config = ConfigLoader.load(configFile.toString());

        assertEquals("xmpp.example.com", config.getXmpp().getHost());
        assertEquals(AppConfig.XmppConfig.TlsMode.STARTTLS, config.getXmpp().getTlsMode());
        assertEquals("SCRAM-SHA-256", config.getXmpp().getSaslMechanism());
        assertEquals(45, config.getXmpp().getHandshakeTimeout());
        assertEquals(120, config.getXmpp().getReconnectInitialDelay());
        assertEquals(900, config.getXmpp().getReconnectMaxDelay());
    }
}
