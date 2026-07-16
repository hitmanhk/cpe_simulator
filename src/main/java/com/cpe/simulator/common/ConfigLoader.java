package com.cpe.simulator.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static AppConfig load(String configPath) throws IOException {
        if (configPath != null && !configPath.isEmpty()) {
            return YAML_MAPPER.readValue(Path.of(configPath).toFile(), AppConfigWrapper.class).getSimulator();
        }
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yaml")) {
            if (is == null) {
                return new AppConfig();
            }
            return YAML_MAPPER.readValue(is, AppConfigWrapper.class).getSimulator();
        }
    }

    public static AppConfig loadDefault() {
        try {
            return load(null);
        } catch (IOException e) {
            return new AppConfig();
        }
    }

    public static AppConfig merge(AppConfig base, AppConfig overrides) {
        if (overrides.getMode() != null && !overrides.getMode().equals("public")) {
            base.setMode(overrides.getMode());
        }
        if (overrides.getCount() != 100) {
            base.setCount(overrides.getCount());
        }
        if (overrides.getAcsUrl() != null && !overrides.getAcsUrl().equals("http://localhost:7547/")) {
            base.setAcsUrl(overrides.getAcsUrl());
        }
        return base;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AppConfigWrapper {
        private AppConfig simulator;
        private AppConfig.XmppConfig xmpp;

        public AppConfig getSimulator() {
            AppConfig config = simulator != null ? simulator : new AppConfig();
            if (xmpp != null) {
                config.setXmpp(xmpp);
            }
            return config;
        }

        public void setSimulator(AppConfig simulator) {
            this.simulator = simulator;
        }

        public void setXmpp(AppConfig.XmppConfig xmpp) {
            this.xmpp = xmpp;
        }
    }
}
