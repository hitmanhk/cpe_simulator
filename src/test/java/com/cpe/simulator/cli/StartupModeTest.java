package com.cpe.simulator.cli;

import com.cpe.simulator.common.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StartupModeTest {

    @Test
    void testXmppOnlyConfigDefaults() {
        AppConfig config = new AppConfig();
        assertFalse(config.isXmppOnly());
    }

    @Test
    void testXmppOnlyConfigSetterGetter() {
        AppConfig config = new AppConfig();

        config.setXmppOnly(true);
        assertTrue(config.isXmppOnly());

        config.setXmppOnly(false);
        assertFalse(config.isXmppOnly());

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
    void testZeroPeriodicIntervalDisablesSchedule() {
        AppConfig config = new AppConfig();
        config.setInformInterval(0);

        assertEquals(0, config.getInformSchedules().getPeriodic().getInterval());
        assertDoesNotThrow(config::validateInformSchedules);
    }

    @Test
    void testXmppOnlyWithZeroPeriodicInterval() {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.setXmppOnly(true);
        config.getInformSchedules().getPeriodic().setInterval(0);

        assertTrue(config.isXmppOnly());
        assertEquals(0, config.getInformSchedules().getPeriodic().getInterval());
        assertTrue(config.isNatMode());
    }

    @Test
    void testXmppOnlyKeepsScheduledInformConfiguration() {
        AppConfig config = new AppConfig();
        config.setMode("nat");
        config.setXmppOnly(true);

        assertTrue(config.isXmppOnly());
        assertTrue(config.getInformSchedules().hasEnabledSchedule());
    }

    @Test
    void testNormalModeSchedulesAfterStartupAndXmppOnlyWaitsForOnline() {
        AppConfig xmppOnly = new AppConfig();
        xmppOnly.setXmppOnly(true);
        assertTrue(xmppOnly.isXmppOnly() && xmppOnly.getInformSchedules().hasEnabledSchedule());

        AppConfig allDisabled = new AppConfig();
        allDisabled.getInformSchedules().getBootstrap().setInterval(0);
        allDisabled.getInformSchedules().getBoot().setInterval(0);
        allDisabled.getInformSchedules().getPeriodic().setInterval(0);
        assertFalse(!allDisabled.isXmppOnly() && allDisabled.getInformSchedules().hasEnabledSchedule());

        AppConfig normal = new AppConfig();
        assertTrue(!normal.isXmppOnly() && normal.getInformSchedules().hasEnabledSchedule());
    }
}
