package com.cpe.simulator.common;

import java.util.UUID;

public class IdGenerator {

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String serialNumber(String prefix, int index) {
        return prefix + String.format("%05d", index);
    }

    public static String cwmpId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String deviceId(String oui, String productClass, String serialNumber) {
        return oui + "-" + productClass + "-" + serialNumber;
    }

    public static String randomIp() {
        return String.format("10.%d.%d.%d",
            (int)(Math.random() * 256),
            (int)(Math.random() * 256),
            (int)(Math.random() * 254) + 1);
    }
}
