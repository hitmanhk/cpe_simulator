package com.cpe.simulator.cli;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConfigLoader;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.engine.SimulationEngine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.CountDownLatch;

@Command(name = "start", description = "Start the CPE simulator")
public class StartCommand implements Runnable {

    @Option(names = {"--mode", "-m"}, description = "Running mode: public or nat", defaultValue = "public")
    String mode;

    @Option(names = {"--count", "-c"}, description = "Number of CPE instances", defaultValue = "100")
    int count;

    @Option(names = {"--acs-url"}, description = "ACS URL", required = true)
    String acsUrl;

    @Option(names = {"--acs-user"}, description = "ACS username")
    String acsUser;

    @Option(names = {"--acs-password"}, description = "ACS password")
    String acsPassword;

    @Option(names = {"--model"}, description = "Data model: tr181, tr098, or file path. Shortcut names resolve to models/ directory next to the JAR", defaultValue = "tr181")
    String model;

    @Option(names = {"--oui"}, description = "OUI prefix", defaultValue = "000000")
    String oui;

    @Option(names = {"--product-class"}, description = "Product class", defaultValue = "SimCPE")
    String productClass;

    @Option(names = {"--serial-prefix"}, description = "Serial number prefix", defaultValue = "SIM")
    String serialPrefix;

    @Option(names = {"--serial-start"}, description = "Serial number start index", defaultValue = "0")
    int serialStart;

    @Option(names = {"--inform-interval"}, description = "Inform interval in seconds", defaultValue = "300")
    int informInterval;

    @Option(names = {"--inform-jitter"}, description = "Inform jitter ratio (0.0-1.0)", defaultValue = "0.3")
    double informJitter;

    @Option(names = {"--max-sessions"}, description = "Max concurrent CWMP sessions", defaultValue = "1000")
    int maxSessions;

    @Option(names = {"--connect-batch"}, description = "XMPP batch connect size", defaultValue = "1000")
    int connectBatch;

    @Option(names = {"--connect-batch-delay"}, description = "Batch delay in ms", defaultValue = "200")
    int connectBatchDelay;

    @Option(names = {"--boot-batch-size"}, description = "BOOT inform batch size", defaultValue = "100")
    int bootBatchSize;

    @Option(names = {"--boot-batch-delay"}, description = "BOOT batch delay in ms", defaultValue = "1000")
    int bootBatchDelay;

    @Option(names = {"--boot-jitter"}, description = "BOOT inform jitter ratio (0.0-1.0)", defaultValue = "0.5")
    double bootJitter;

    @Option(names = {"--conn-req-host"}, description = "Connection request listen host", defaultValue = "0.0.0.0")
    String connReqHost;

    @Option(names = {"--conn-req-port"}, description = "Connection request listen port", defaultValue = "7547")
    int connReqPort;

    @Option(names = {"--conn-req-user"}, description = "Connection request auth user")
    String connReqUser;

    @Option(names = {"--conn-req-password"}, description = "Connection request auth password")
    String connReqPassword;

    @Option(names = {"--xmpp-host"}, description = "XMPP server host")
    String xmppHost;

    @Option(names = {"--xmpp-port"}, description = "XMPP server port (default: 5223 for DIRECT TLS, otherwise 5222)")
    Integer xmppPort;

    @Option(names = {"--xmpp-domain"}, description = "XMPP domain")
    String xmppDomain;

    @Option(names = {"--xmpp-password"}, description = "XMPP password")
    String xmppPassword;

    @Option(names = {"--xmpp-user-prefix"}, description = "XMPP username prefix", defaultValue = "cpe")
    String xmppUserPrefix;

    @Option(names = {"--xmpp-resource"}, description = "XMPP resource", defaultValue = "sim")
    String xmppResource;

    @Option(names = {"--xmpp-tls-mode"}, description = "XMPP TLS mode: NONE, DIRECT, or STARTTLS")
    String xmppTlsMode;

    @Option(names = {"--xmpp-sasl-mechanism"}, description = "XMPP SASL mechanism: PLAIN, SCRAM-SHA-1, or SCRAM-SHA-256")
    String xmppSaslMechanism;

    @Option(names = {"--reboot-delay"}, description = "Reboot delay in seconds", defaultValue = "5")
    int rebootDelay;

    @Option(names = {"--download-delay"}, description = "Download delay in seconds", defaultValue = "10")
    int downloadDelay;

    @Option(names = {"--download-success-rate"}, description = "Download success rate (0.0-1.0)", defaultValue = "0.99")
    double downloadSuccessRate;

    @Option(names = {"--config"}, description = "Config file path")
    String configPath;

    @Option(names = {"--xmpp-only"}, description = "XMPP-only mode: skip CwmpServer, BOOT inform, and periodic reports")
    boolean xmppOnly;

    @Option(names = {"--no-periodic"}, description = "Disable periodic inform reports")
    boolean noPeriodic;

    @Option(names = {"--socket-path"}, description = "UDS socket path (default: platform-specific)")
    String socketPath;

    public String validateNatMode() {
        if (xmppOnly && !"nat".equalsIgnoreCase(mode)) {
            return "--xmpp-only is only available in NAT mode";
        }
        if ("nat".equalsIgnoreCase(mode)) {
            if (xmppHost == null || xmppHost.isEmpty()) {
                return "--xmpp-host is required in NAT mode";
            }
            if (xmppDomain == null || xmppDomain.isEmpty()) {
                return "--xmpp-domain is required in NAT mode";
            }
            if (xmppPassword == null || xmppPassword.isEmpty()) {
                return "--xmpp-password is required in NAT mode";
            }
        }
        return null;
    }

    @Override
    public void run() {
        String validationError = validateNatMode();
        if (validationError != null) {
            System.err.println("Error: " + validationError);
            System.exit(1);
        }

        AppConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            System.exit(1);
            return;
        }

        applyCliOverrides(config);

        ProgressBar progressBar = new ProgressBar();
        progressBar.update("Loading config", 1, 4);

        SimulationEngine engine = new SimulationEngine(config);

        progressBar.update("Starting engine", 2, 4);

        UdsServer udsServer = socketPath != null
                ? new UdsServer(engine, socketPath)
                : new UdsServer(engine);
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered...");
            udsServer.stop();
            engine.stop();
            shutdownLatch.countDown();
        }));

        try {
            engine.start();
            progressBar.update("Engine started", 3, 4);

            udsServer.start();
            progressBar.update("UDS server started", 4, 4);

            progressBar.complete("Simulator started successfully");

            System.out.println("Mode: " + config.getMode());
            System.out.println("Devices: " + config.getCount());
            System.out.println("ACS URL: " + config.getAcsUrl());
            System.out.println("Press 'h' for help");

            Thread.ofVirtual().name("uds-shutdown-watcher").start(() -> {
                while (!udsServer.isShutdownRequested()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("Stop requested via UDS...");
                engine.stop();
                udsServer.stop();
                shutdownLatch.countDown();
            });

            KeyboardHandler keyboardHandler = new KeyboardHandler(engine);
            keyboardHandler.start();

            shutdownLatch.await();

        } catch (Exception e) {
            System.err.println("Failed to start simulator: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void applyCliOverrides(AppConfig config) {
        config.setMode(mode);
        config.setCount(count);
        config.setAcsUrl(acsUrl);
        if (acsUser != null) config.setAcsUser(acsUser);
        if (acsPassword != null) config.setAcsPassword(acsPassword);
        config.setModel(model);
        config.setOui(oui);
        config.setProductClass(productClass);
        config.setSerialPrefix(serialPrefix);
        config.setSerialStart(serialStart);
        config.setInformInterval(informInterval);
        config.setInformJitter(informJitter);
        config.setMaxSessions(maxSessions);
        config.setConnectBatch(connectBatch);
        config.setConnectBatchDelay(connectBatchDelay);
        config.setBootBatchSize(bootBatchSize);
        config.setBootBatchDelay(bootBatchDelay);
        config.setBootJitter(bootJitter);
        config.setConnReqHost(connReqHost);
        config.setConnReqPort(connReqPort);
        if (connReqUser != null) config.setConnReqUser(connReqUser);
        if (connReqPassword != null) config.setConnReqPassword(connReqPassword);
        config.setRebootDelay(rebootDelay);
        config.setDownloadDelay(downloadDelay);
        config.setDownloadSuccessRate(downloadSuccessRate);
        config.setXmppOnly(xmppOnly);
        config.setNoPeriodic(noPeriodic);

        AppConfig.XmppConfig xmpp = config.getXmpp();
        if (xmppHost != null) xmpp.setHost(xmppHost);
        if (xmppPort != null) xmpp.setPort(xmppPort);
        if (xmppDomain != null) xmpp.setDomain(xmppDomain);
        if (xmppPassword != null) xmpp.setPassword(xmppPassword);
        xmpp.setUserPrefix(xmppUserPrefix);
        xmpp.setResource(xmppResource);
        if (xmppTlsMode != null) {
            xmpp.setTlsMode(xmppTlsMode);
        }
        if (xmppSaslMechanism != null) xmpp.setSaslMechanism(xmppSaslMechanism);
    }
}
