package com.cpe.simulator.engine;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceMonitor {

    public static long getJvmMemoryUsed() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public static long getJvmMemoryMax() {
        return Runtime.getRuntime().maxMemory();
    }

    public static int getThreadCount() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return threadBean.getThreadCount();
    }

    public static int getSocketCount() {
        try {
            Path fdDir = Path.of("/proc/self/fd");
            if (Files.isDirectory(fdDir)) {
                int count = 0;
                try (var stream = Files.list(fdDir)) {
                    for (Path fd : stream.toList()) {
                        try {
                            String target = Files.readSymbolicLink(fd).toString();
                            if (target.startsWith("socket:")) {
                                count++;
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
                return count;
            }
        } catch (IOException ignored) {
        }
        return -1;
    }
}
