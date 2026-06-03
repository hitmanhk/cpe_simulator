package com.cpe.simulator.cli;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StartupModeTest {

    @Test
    void testXmppOnlyConfigDefaults() {
        AppConfig config = new AppConfig();
        assertFalse(config.isXmppOnly());
        assertFalse(config.isNoPeriodic());
    }

    @Test
    void testXmppOnlyConfigSetterGetter() {
        AppConfig config = new AppConfig();

        config.setXmppOnly(true);
        assertTrue(config.isXmppOnly());

        config.setXmppOnly(false);
        assertFalse(config.isXmppOnly());

        config.setNoPeriodic(true);
        assertTrue(config.isNoPeriodic());

        config.setNoPeriodic(false);
        assertFalse(config.isNoPeriodic());
    }

    @Test
    void testXmppOnlyRequiresNatMode() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "public";
        cmd.xmppOnly = true;
        cmd.acsUrl = "http://localhost:7547/";

        String error = cmd.validateNatMode();
        assertNotNull(error);
        assertTrue(error.contains("--xmpp-only"));
        assertTrue(error.contains("NAT mode"));
    }

    @Test
    void testXmppOnlyAllowedInNatMode() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.xmppOnly = true;
        cmd.acsUrl = "http://localhost:7547/";
        cmd.xmppHost = "xmpp.example.com";
        cmd.xmppDomain = "iot.example.com";
        cmd.xmppPassword = "secret";

        String error = cmd.validateNatMode();
        assertNull(error);
    }

    @Test
    void testNoPeriodicAllowedInPublicMode() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "public";
        cmd.noPeriodic = true;
        cmd.acsUrl = "http://localhost:7547/";

        String error = cmd.validateNatMode();
        assertNull(error);
    }

    @Test
    void testNoPeriodicAllowedInNatMode() {
        StartCommand cmd = new StartCommand();
        cmd.mode = "nat";
        cmd.noPeriodic = true;
        cmd.acsUrl = "http://localhost:7547/";
        cmd.xmppHost = "xmpp.example.com";
        cmd.xmppDomain = "iot.example.com";
        cmd.xmppPassword = "secret";

        String error = cmd.validateNatMode();
        assertNull(error);
    }

    @Test
    void testXmppOnlyWithNoPeriodicBothSet() {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.setXmppOnly(true);
        config.setNoPeriodic(true);

        assertTrue(config.isXmppOnly());
        assertTrue(config.isNoPeriodic());
        assertTrue(config.isNatMode());
    }

    @Test
    void testXmppOnlyImpliesNoPeriodicBehavior() {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.setXmppOnly(true);

        assertTrue(config.isXmppOnly());
        assertFalse(config.isNoPeriodic());

        boolean shouldSkipPeriodic = config.isXmppOnly() || config.isNoPeriodic();
        assertTrue(shouldSkipPeriodic);
    }

    @Test
    void testPeriodicInformCondition() {
        AppConfig xmppOnly = new AppConfig();
        xmppOnly.setXmppOnly(true);
        assertFalse(!xmppOnly.isXmppOnly() && !xmppOnly.isNoPeriodic());

        AppConfig noPeriodic = new AppConfig();
        noPeriodic.setNoPeriodic(true);
        assertFalse(!noPeriodic.isXmppOnly() && !noPeriodic.isNoPeriodic());

        AppConfig both = new AppConfig();
        both.setXmppOnly(true);
        both.setNoPeriodic(true);
        assertFalse(!both.isXmppOnly() && !both.isNoPeriodic());

        AppConfig normal = new AppConfig();
        assertTrue(!normal.isXmppOnly() && !normal.isNoPeriodic());
    }
}
