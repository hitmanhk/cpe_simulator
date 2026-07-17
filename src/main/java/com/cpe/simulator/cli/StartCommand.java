package com.cpe.simulator.cli;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.common.ConfigLoader;
import com.cpe.simulator.common.SimLogger;
import com.cpe.simulator.engine.SimulationEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.CountDownLatch;

@Command(name = "start", description = "Start the CPE simulator")
public class StartCommand implements Runnable {

    @Option(names = {"--mode", "-m"}, description = "Running mode: public or nat", defaultValue = "public")
    String mode;

    @Option(names = {"--count", "-c"}, description = "Number of CPE instances", defaultValue = "100")
    int count;

    @Option(names = {"--acs-url"}, description = "ACS URL")
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

    @Option(names = {"--periodic-inform-interval"}, description = "PERIODIC inform interval in seconds", defaultValue = "300")
    int informInterval;

    @Option(names = {"--periodic-inform-initial-jitter"}, description = "PERIODIC inform initial jitter ratio (0.0-1.0)", defaultValue = "0.3")
    double informJitter;

    @Option(names = {"--bootstrap-inform-interval"}, description = "BOOTSTRAP inform interval in seconds")
    Integer bootstrapInformInterval;

    @Option(names = {"--bootstrap-inform-initial-jitter"}, description = "BOOTSTRAP inform initial jitter ratio (0.0-1.0)")
    Double bootstrapInformInitialJitter;

    @Option(names = {"--boot-inform-interval"}, description = "Scheduled BOOT inform interval in seconds")
    Integer bootInformInterval;

    @Option(names = {"--boot-inform-initial-jitter"}, description = "Scheduled BOOT inform initial jitter ratio (0.0-1.0)")
    Double bootInformInitialJitter;

    @Option(names = {"--max-sessions"}, description = "Max concurrent CWMP sessions", defaultValue = "1000")
    int maxSessions;

    @Option(names = {"--connect-batch"}, description = "XMPP batch connect size", defaultValue = "1000")
    int connectBatch;

    @Option(names = {"--connect-batch-delay"}, description = "Batch delay in ms", defaultValue = "200")
    int connectBatchDelay;

    @Option(names = {"--boot-batch-size"}, description = "Startup BOOTSTRAP inform batch size", defaultValue = "100")
    int bootBatchSize;

    @Option(names = {"--boot-batch-delay"}, description = "Startup BOOTSTRAP batch delay in ms", defaultValue = "1000")
    int bootBatchDelay;

    @Option(names = {"--boot-jitter"}, description = "Startup BOOTSTRAP inform jitter ratio (0.0-1.0)", defaultValue = "0.5")
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

    @Option(names = {"--xmpp-only"}, description = "XMPP-only mode: skip CwmpServer and startup BOOTSTRAP; start scheduled reports after XMPP is online")
    boolean xmppOnly;

    @Option(names = {"--socket-path"}, description = "UDS socket path (default: platform-specific)")
    String socketPath;

    @Spec
    CommandSpec commandSpec;

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
        AppConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            System.exit(1);
            return;
        }

        applyCliOverrides(config);

        String validationError = validateNatMode(config);
        if (validationError == null) {
            try {
                config.validateInformSchedules();
            } catch (IllegalArgumentException e) {
                validationError = e.getMessage();
            }
        }
        if (validationError != null) {
            throw new CommandLine.ParameterException(commandSpec.commandLine(), validationError);
        }

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

    void applyCliOverrides(AppConfig config) {
        if (wasSpecified("--mode")) config.setMode(mode);
        if (wasSpecified("--count")) config.setCount(count);
        if (wasSpecified("--acs-url")) config.setAcsUrl(acsUrl);
        if (wasSpecified("--acs-user")) config.setAcsUser(acsUser);
        if (wasSpecified("--acs-password")) config.setAcsPassword(acsPassword);
        if (wasSpecified("--model")) config.setModel(model);
        if (wasSpecified("--oui")) config.setOui(oui);
        if (wasSpecified("--product-class")) config.setProductClass(productClass);
        if (wasSpecified("--serial-prefix")) config.setSerialPrefix(serialPrefix);
        if (wasSpecified("--serial-start")) config.setSerialStart(serialStart);
        if (wasSpecified("--max-sessions")) config.setMaxSessions(maxSessions);
        if (wasSpecified("--connect-batch")) config.setConnectBatch(connectBatch);
        if (wasSpecified("--connect-batch-delay")) config.setConnectBatchDelay(connectBatchDelay);
        if (wasSpecified("--boot-batch-size")) config.setBootBatchSize(bootBatchSize);
        if (wasSpecified("--boot-batch-delay")) config.setBootBatchDelay(bootBatchDelay);
        if (wasSpecified("--boot-jitter")) config.setBootJitter(bootJitter);
        if (wasSpecified("--conn-req-host")) config.setConnReqHost(connReqHost);
        if (wasSpecified("--conn-req-port")) config.setConnReqPort(connReqPort);
        if (wasSpecified("--conn-req-user")) config.setConnReqUser(connReqUser);
        if (wasSpecified("--conn-req-password")) config.setConnReqPassword(connReqPassword);
        if (wasSpecified("--reboot-delay")) config.setRebootDelay(rebootDelay);
        if (wasSpecified("--download-delay")) config.setDownloadDelay(downloadDelay);
        if (wasSpecified("--download-success-rate")) config.setDownloadSuccessRate(downloadSuccessRate);
        if (wasSpecified("--xmpp-only")) config.setXmppOnly(xmppOnly);

        AppConfig.InformSchedules schedules = config.getInformSchedules();
        if (wasSpecified("--periodic-inform-interval")) schedules.getPeriodic().setInterval(informInterval);
        if (wasSpecified("--periodic-inform-initial-jitter")) schedules.getPeriodic().setInitialJitter(informJitter);
        if (wasSpecified("--bootstrap-inform-interval")) schedules.getBootstrap().setInterval(bootstrapInformInterval);
        if (wasSpecified("--bootstrap-inform-initial-jitter")) schedules.getBootstrap().setInitialJitter(bootstrapInformInitialJitter);
        if (wasSpecified("--boot-inform-interval")) schedules.getBoot().setInterval(bootInformInterval);
        if (wasSpecified("--boot-inform-initial-jitter")) schedules.getBoot().setInitialJitter(bootInformInitialJitter);

        AppConfig.XmppConfig xmpp = config.getXmpp();
        if (wasSpecified("--xmpp-host")) xmpp.setHost(xmppHost);
        if (wasSpecified("--xmpp-port")) xmpp.setPort(xmppPort);
        if (wasSpecified("--xmpp-domain")) xmpp.setDomain(xmppDomain);
        if (wasSpecified("--xmpp-password")) xmpp.setPassword(xmppPassword);
        if (wasSpecified("--xmpp-user-prefix")) xmpp.setUserPrefix(xmppUserPrefix);
        if (wasSpecified("--xmpp-resource")) xmpp.setResource(xmppResource);
        if (wasSpecified("--xmpp-tls-mode")) xmpp.setTlsMode(xmppTlsMode);
        if (wasSpecified("--xmpp-sasl-mechanism")) xmpp.setSaslMechanism(xmppSaslMechanism);
    }

    private boolean wasSpecified(String... optionNames) {
        if (commandSpec == null || commandSpec.commandLine().getParseResult() == null) {
            return true;
        }
        for (String optionName : optionNames) {
            if (commandSpec.commandLine().getParseResult().hasMatchedOption(optionName)) {
                return true;
            }
        }
        return false;
    }

    private String validateNatMode(AppConfig config) {
        if (config.isXmppOnly() && !config.isNatMode()) {
            return "--xmpp-only is only available in NAT mode";
        }
        if (config.isNatMode()) {
            if (config.getXmpp().getHost() == null || config.getXmpp().getHost().isEmpty()) {
                return "--xmpp-host is required in NAT mode";
            }
            if (config.getXmpp().getDomain() == null || config.getXmpp().getDomain().isEmpty()) {
                return "--xmpp-domain is required in NAT mode";
            }
            if (config.getXmpp().getPassword() == null || config.getXmpp().getPassword().isEmpty()) {
                return "--xmpp-password is required in NAT mode";
            }
        }
        return null;
    }
}
