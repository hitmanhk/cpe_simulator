package com.cpe.simulator.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    @JsonProperty("mode")
    private String mode = "public";

    @JsonProperty("count")
    private int count = 100;

    @JsonProperty("acs-url")
    private String acsUrl = "http://localhost:7547/";

    @JsonProperty("acs-user")
    private String acsUser = "admin";

    @JsonProperty("acs-password")
    private String acsPassword = "admin";

    @JsonProperty("model")
    private String model = "tr181";

    @JsonProperty("oui")
    private String oui = "000000";

    @JsonProperty("product-class")
    private String productClass = "SimCPE";

    @JsonProperty("serial-prefix")
    private String serialPrefix = "SIM";

    @JsonProperty("serial-start")
    private int serialStart = 0;

    @JsonProperty("inform-schedules")
    private InformSchedules informSchedules = new InformSchedules();

    @JsonProperty("max-sessions")
    private int maxSessions = 1000;

    @JsonProperty("connect-batch")
    private int connectBatch = 1000;

    @JsonProperty("connect-batch-delay")
    private int connectBatchDelay = 200;

    @JsonProperty("boot-batch-size")
    private int bootBatchSize = 100;

    @JsonProperty("boot-batch-delay")
    private int bootBatchDelay = 1000;

    @JsonProperty("boot-jitter")
    private double bootJitter = 0.5;

    @JsonProperty("conn-req-host")
    private String connReqHost = "0.0.0.0";

    @JsonProperty("conn-req-port")
    private int connReqPort = 7547;

    @JsonProperty("conn-req-user")
    private String connReqUser;

    @JsonProperty("conn-req-password")
    private String connReqPassword;

    @JsonProperty("reboot-delay")
    private int rebootDelay = 5;

    @JsonProperty("download-delay")
    private int downloadDelay = 10;

    @JsonProperty("download-success-rate")
    private double downloadSuccessRate = 0.99;

    @JsonProperty("session-timeout")
    private int sessionTimeout = 30;

    @JsonProperty("xmpp-only")
    private boolean xmppOnly = false;

    @JsonProperty("local-address")
    private String localAddress;

    private XmppConfig xmpp = new XmppConfig();
    private LoggingConfig logging = new LoggingConfig();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public String getAcsUrl() { return acsUrl; }
    public void setAcsUrl(String acsUrl) { this.acsUrl = acsUrl; }
    public String getAcsUser() { return acsUser; }
    public void setAcsUser(String acsUser) { this.acsUser = acsUser; }
    public String getAcsPassword() { return acsPassword; }
    public void setAcsPassword(String acsPassword) { this.acsPassword = acsPassword; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getOui() { return oui; }
    public void setOui(String oui) { this.oui = oui; }
    public String getProductClass() { return productClass; }
    public void setProductClass(String productClass) { this.productClass = productClass; }
    public String getSerialPrefix() { return serialPrefix; }
    public void setSerialPrefix(String serialPrefix) { this.serialPrefix = serialPrefix; }
    public int getSerialStart() { return serialStart; }
    public void setSerialStart(int serialStart) { this.serialStart = serialStart; }
    @JsonProperty("inform-interval")
    public int getInformInterval() { return informSchedules.getPeriodic().getInterval(); }
    @JsonProperty("inform-interval")
    public void setInformInterval(int informInterval) { informSchedules.getPeriodic().setInterval(informInterval); }
    @JsonProperty("inform-jitter")
    public double getInformJitter() { return informSchedules.getPeriodic().getInitialJitter(); }
    @JsonProperty("inform-jitter")
    public void setInformJitter(double informJitter) { informSchedules.getPeriodic().setInitialJitter(informJitter); }
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    public int getConnectBatch() { return connectBatch; }
    public void setConnectBatch(int connectBatch) { this.connectBatch = connectBatch; }
    public int getConnectBatchDelay() { return connectBatchDelay; }
    public void setConnectBatchDelay(int connectBatchDelay) { this.connectBatchDelay = connectBatchDelay; }
    public int getBootBatchSize() { return bootBatchSize; }
    public void setBootBatchSize(int bootBatchSize) { this.bootBatchSize = bootBatchSize; }
    public int getBootBatchDelay() { return bootBatchDelay; }
    public void setBootBatchDelay(int bootBatchDelay) { this.bootBatchDelay = bootBatchDelay; }
    public double getBootJitter() { return bootJitter; }
    public void setBootJitter(double bootJitter) { this.bootJitter = bootJitter; }
    public String getConnReqHost() { return connReqHost; }
    public void setConnReqHost(String connReqHost) { this.connReqHost = connReqHost; }
    public int getConnReqPort() { return connReqPort; }
    public void setConnReqPort(int connReqPort) { this.connReqPort = connReqPort; }
    public String getConnReqUser() { return connReqUser; }
    public void setConnReqUser(String connReqUser) { this.connReqUser = connReqUser; }
    public String getConnReqPassword() { return connReqPassword; }
    public void setConnReqPassword(String connReqPassword) { this.connReqPassword = connReqPassword; }
    public int getRebootDelay() { return rebootDelay; }
    public void setRebootDelay(int rebootDelay) { this.rebootDelay = rebootDelay; }
    public int getDownloadDelay() { return downloadDelay; }
    public void setDownloadDelay(int downloadDelay) { this.downloadDelay = downloadDelay; }
    public double getDownloadSuccessRate() { return downloadSuccessRate; }
    public void setDownloadSuccessRate(double downloadSuccessRate) { this.downloadSuccessRate = downloadSuccessRate; }
    public int getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(int sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    public InformSchedules getInformSchedules() { return informSchedules; }
    public void setInformSchedules(InformSchedules informSchedules) {
        this.informSchedules = informSchedules != null ? informSchedules : new InformSchedules();
    }
    public boolean isXmppOnly() { return xmppOnly; }
    public void setXmppOnly(boolean xmppOnly) { this.xmppOnly = xmppOnly; }
    public String getLocalAddress() { return localAddress; }
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public XmppConfig getXmpp() { return xmpp; }
    public void setXmpp(XmppConfig xmpp) { this.xmpp = xmpp; }
    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging; }

    public boolean isNatMode() {
        return "nat".equalsIgnoreCase(mode);
    }

    public boolean isConnReqAuthEnabled() {
        return connReqUser != null && !connReqUser.isEmpty()
            && connReqPassword != null && !connReqPassword.isEmpty();
    }

    public void validateInformSchedules() {
        informSchedules.validate();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InformSchedules {
        private InformSchedule bootstrap = new InformSchedule(86400, 0.2);
        private InformSchedule boot = new InformSchedule(3600, 0.2);
        private InformSchedule periodic = new InformSchedule(300, 0.3);

        public InformSchedule getBootstrap() { return bootstrap; }
        public void setBootstrap(InformSchedule bootstrap) {
            if (bootstrap != null) this.bootstrap.mergeFrom(bootstrap);
        }
        public InformSchedule getBoot() { return boot; }
        public void setBoot(InformSchedule boot) {
            if (boot != null) this.boot.mergeFrom(boot);
        }
        public InformSchedule getPeriodic() { return periodic; }
        public void setPeriodic(InformSchedule periodic) {
            if (periodic != null) this.periodic.mergeFrom(periodic);
        }

        public boolean hasEnabledSchedule() {
            return bootstrap.getInterval() > 0 || boot.getInterval() > 0 || periodic.getInterval() > 0;
        }

        private void validate() {
            bootstrap.validate("bootstrap");
            boot.validate("boot");
            periodic.validate("periodic");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InformSchedule {
        @JsonProperty("interval")
        private Integer interval;
        @JsonProperty("initial-jitter")
        private Double initialJitter;

        public InformSchedule() {}

        public InformSchedule(int interval, double initialJitter) {
            this.interval = interval;
            this.initialJitter = initialJitter;
        }

        public int getInterval() { return interval != null ? interval : 0; }
        public void setInterval(int interval) { this.interval = interval; }
        public double getInitialJitter() { return initialJitter != null ? initialJitter : 0.0; }
        public void setInitialJitter(double initialJitter) { this.initialJitter = initialJitter; }

        private void mergeFrom(InformSchedule overrides) {
            if (overrides.interval != null) interval = overrides.interval;
            if (overrides.initialJitter != null) initialJitter = overrides.initialJitter;
        }

        private void validate(String name) {
            if (getInterval() < 0) {
                throw new IllegalArgumentException(name + " inform interval must be greater than or equal to 0");
            }
            if (getInitialJitter() < 0.0 || getInitialJitter() > 1.0) {
                throw new IllegalArgumentException(name + " initial-jitter must be between 0.0 and 1.0");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XmppConfig {
        public enum TlsMode {
            NONE, DIRECT, STARTTLS;

            @JsonCreator
            public static TlsMode fromValue(String value) {
                if (value == null || value.isBlank()) {
                    return NONE;
                }
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }

        @JsonProperty("host")
        private String host = "localhost";
        @JsonProperty("port")
        private Integer port;
        @JsonProperty("domain")
        private String domain = "iot.example.com";
        @JsonProperty("user-prefix")
        private String userPrefix = "cpe";
        @JsonProperty("password")
        private String password = "secret";
        @JsonProperty("resource")
        private String resource = "sim";
        @JsonProperty("tls-mode")
        private TlsMode tlsMode;
        /** Legacy configuration: {@code tls: true} means direct TLS. */
        @JsonProperty("tls")
        private Boolean tls;
        @JsonProperty("sasl-mechanism")
        private String saslMechanism = "SCRAM-SHA-1";
        @JsonProperty("keepalive-interval")
        private int keepaliveInterval = 60;
        @JsonProperty("keepalive-timeout")
        private int keepaliveTimeout = 30;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port != null ? port : (isDirectTls() ? 5223 : 5222); }
        public void setPort(int port) { this.port = port; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getUserPrefix() { return userPrefix; }
        public void setUserPrefix(String userPrefix) { this.userPrefix = userPrefix; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public TlsMode getTlsMode() {
            return tlsMode != null ? tlsMode : (Boolean.TRUE.equals(tls) ? TlsMode.DIRECT : TlsMode.NONE);
        }
        public void setTlsMode(TlsMode tlsMode) { this.tlsMode = tlsMode; }
        public void setTlsMode(String tlsMode) { this.tlsMode = TlsMode.fromValue(tlsMode); }
        public boolean isDirectTls() { return getTlsMode() == TlsMode.DIRECT; }
        public boolean isStartTls() { return getTlsMode() == TlsMode.STARTTLS; }
        /** @deprecated use {@link #getTlsMode()} */
        @Deprecated
        public boolean isTls() { return isDirectTls(); }
        /** @deprecated use {@link #setTlsMode(TlsMode)} */
        @Deprecated
        public void setTls(boolean tls) {
            this.tls = tls;
            this.tlsMode = tls ? TlsMode.DIRECT : TlsMode.NONE;
        }
        public String getSaslMechanism() { return saslMechanism; }
        public void setSaslMechanism(String saslMechanism) { this.saslMechanism = saslMechanism; }
        public int getKeepaliveInterval() { return keepaliveInterval; }
        public void setKeepaliveInterval(int keepaliveInterval) { this.keepaliveInterval = keepaliveInterval; }
        public int getKeepaliveTimeout() { return keepaliveTimeout; }
        public void setKeepaliveTimeout(int keepaliveTimeout) { this.keepaliveTimeout = keepaliveTimeout; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoggingConfig {
        @JsonProperty("level")
        private String level = "INFO";
        @JsonProperty("device-filter")
        private String deviceFilter = "";

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getDeviceFilter() { return deviceFilter; }
        public void setDeviceFilter(String deviceFilter) { this.deviceFilter = deviceFilter; }
    }
}
