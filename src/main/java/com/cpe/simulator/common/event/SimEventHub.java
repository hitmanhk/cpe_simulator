package com.cpe.simulator.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Static publish/subscribe hub for {@link SimEvent}s. The hub is optional
 * infrastructure: with no listeners registered, publishing costs a single
 * empty-list check, so CLI mode carries zero overhead.
 * <p>
 * Listener exceptions are isolated — a misbehaving listener can never affect
 * the engine or other listeners.
 */
public final class SimEventHub {

    private static final Logger LOG = LoggerFactory.getLogger(SimEventHub.class);
    private static final CopyOnWriteArrayList<Consumer<SimEvent>> LISTENERS = new CopyOnWriteArrayList<>();

    private SimEventHub() {
    }

    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    public static void addListener(Consumer<SimEvent> listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void removeListener(Consumer<SimEvent> listener) {
        LISTENERS.remove(listener);
    }

    /** Removes all listeners. Intended for tests and UI shutdown. */
    public static void clearListeners() {
        LISTENERS.clear();
    }

    public static void publish(SimEvent event) {
        for (Consumer<SimEvent> listener : LISTENERS) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                LOG.warn("SimEvent listener threw {}: {}", t.getClass().getSimpleName(), t.getMessage());
            }
        }
    }
}
