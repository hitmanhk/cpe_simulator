package com.cpe.simulator.desktop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchOptionsTest {

    @Test
    void defaultOptionsAreCaseInsensitiveSubstring() {
        SearchOptions options = SearchOptions.DEFAULT;
        assertTrue(options.matches("sim0001", "000000-SimCPE-SIM0001"));
        assertTrue(options.matches("simcpe", "000000-SimCPE-SIM0001"));
        assertFalse(options.matches("router", "000000-SimCPE-SIM0001"));
    }

    @Test
    void caseSensitiveRespectsCase() {
        SearchOptions options = new SearchOptions(true, false);
        assertTrue(options.matches("SimCPE", "000000-SimCPE-SIM0001"));
        assertFalse(options.matches("simcpe", "000000-SimCPE-SIM0001"));
    }

    @Test
    void wholeWordRequiresBoundaries() {
        SearchOptions options = new SearchOptions(false, true);
        assertTrue(options.matches("SIM0001", "000000-SimCPE-SIM0001"));
        assertFalse(options.matches("IM000", "000000-SimCPE-SIM0001"),
                "词中片段不应命中全字匹配");
        assertTrue(options.matches("9005", "Fault 9005 Invalid"));
        assertFalse(options.matches("900", "Fault 9005 Invalid"));
    }

    @Test
    void wholeWordWithRegexSpecialCharsIsQuoted() {
        SearchOptions options = new SearchOptions(false, true);
        assertTrue(options.matches("Device.WiFi", "路径 Device.WiFi.SSID.1 存在"));
        assertFalse(options.matches("DeviceXWiFi", "路径 Device.WiFi.SSID.1 存在"),
                "点号必须按字面匹配，不应当作正则任意字符");
    }

    @Test
    void emptyAndNullQueriesMatchEverything() {
        SearchOptions options = SearchOptions.DEFAULT;
        assertTrue(options.matches("", "anything"));
        assertTrue(options.matches(null, "anything"));
        assertFalse(options.matches("x", null));
    }
}
