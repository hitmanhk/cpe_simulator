package com.cpe.simulator.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("inform-interval")
    private int informInterval = 300;

    @JsonProperty("inform-jitter")
    private double informJitter = 0.3;

    @JsonProperty("max-sessions")
    private int maxSessions = 1000;

    @JsonProperty("connect-batch")
    private int connectBatch = 1000;

    @JsonProperty("connect-batch-delay")
    private int connectBatchDelay = 200;

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

    @JsonProperty("daemon")
    private boolean daemon = false;

    @JsonProperty("pid-file")
    private String pidFile = "/tmp/cpe-sim.pid";

    @JsonProperty("log-file")
    private String logFile;

    @JsonProperty("session-timeout")
    private int sessionTimeout = 30;

    @JsonProperty("xmpp-only")
    private boolean xmppOnly = false;

    @JsonProperty("no-periodic")
    private boolean noPeriodic = false;

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
    public int getInformInterval() { return informInterval; }
    public void setInformInterval(int informInterval) { this.informInterval = informInterval; }
    public double getInformJitter() { return informJitter; }
    public void setInformJitter(double informJitter) { this.informJitter = informJitter; }
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    public int getConnectBatch() { return connectBatch; }
    public void setConnectBatch(int connectBatch) { this.connectBatch = connectBatch; }
    public int getConnectBatchDelay() { return connectBatchDelay; }
    public void setConnectBatchDelay(int connectBatchDelay) { this.connectBatchDelay = connectBatchDelay; }
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
    public boolean isDaemon() { return daemon; }
    public void setDaemon(boolean daemon) { this.daemon = daemon; }
    public String getPidFile() { return pidFile; }
    public void setPidFile(String pidFile) { this.pidFile = pidFile; }
    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile; }
    public int getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(int sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    public boolean isXmppOnly() { return xmppOnly; }
    public void setXmppOnly(boolean xmppOnly) { this.xmppOnly = xmppOnly; }
    public boolean isNoPeriodic() { return noPeriodic; }
    public void setNoPeriodic(boolean noPeriodic) { this.noPeriodic = noPeriodic; }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XmppConfig {
        @JsonProperty("host")
        private String host = "localhost";
        @JsonProperty("port")
        private int port = 5222;
        @JsonProperty("domain")
        private String domain = "iot.example.com";
        @JsonProperty("user-prefix")
        private String userPrefix = "cpe";
        @JsonProperty("password")
        private String password = "secret";
        @JsonProperty("resource")
        private String resource = "sim";
        @JsonProperty("tls")
        private boolean tls = false;
        @JsonProperty("sasl-mechanism")
        private String saslMechanism = "PLAIN";
        @JsonProperty("keepalive-interval")
        private int keepaliveInterval = 60;
        @JsonProperty("keepalive-timeout")
        private int keepaliveTimeout = 30;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getUserPrefix() { return userPrefix; }
        public void setUserPrefix(String userPrefix) { this.userPrefix = userPrefix; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public boolean isTls() { return tls; }
        public void setTls(boolean tls) { this.tls = tls; }
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
