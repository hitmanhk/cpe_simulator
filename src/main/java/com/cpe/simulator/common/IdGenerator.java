package com.cpe.simulator.common;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {

    private static final AtomicLong CWMP_ID_COUNTER = new AtomicLong(1);

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String serialNumber(String prefix, int index) {
        return prefix + String.format("%05d", index);
    }

    public static String cwmpId() {
        return "SIM-" + CWMP_ID_COUNTER.getAndIncrement();
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
