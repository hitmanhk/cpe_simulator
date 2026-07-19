package com.cpe.simulator.desktop.bridge;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publish/subscribe hub for logback events, mirroring the SimEventHub
 * pattern: no listeners means zero cost for CLI mode.
 */
public final class UiLogSink {

    public record LogEntry(Instant timestamp, String level, String loggerName,
                           String threadName, String message) {
    }

    private static final CopyOnWriteArrayList<Consumer<LogEntry>> LISTENERS = new CopyOnWriteArrayList<>();

    private UiLogSink() {
    }

    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    public static void addListener(Consumer<LogEntry> listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Consumer<LogEntry> listener) {
        LISTENERS.remove(listener);
    }

    public static void publish(LogEntry entry) {
        for (Consumer<LogEntry> listener : LISTENERS) {
            try {
                listener.accept(entry);
            } catch (Throwable ignored) {
                // listener failures must not break logging
            }
        }
    }
}
