package com.cpe.simulator.desktop.bridge;

import com.cpe.simulator.common.event.SimEvent;
import com.cpe.simulator.common.event.SimEventHub;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bridges high-frequency engine events to the FX application thread.
 * Engine threads publish into a lock-free queue; a Timeline drains the queue
 * in batches every {@value #DRAIN_MILLIS} ms so a burst of thousands of CWMP
 * RPC events can never flood the FX thread with runLater calls.
 */
public class UiEventBridge {

    private static final int DRAIN_MILLIS = 250;
    private static final int MAX_BATCH = 5000;

    private final ConcurrentLinkedQueue<SimEvent> queue = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<Consumer<List<SimEvent>>> handlers = new CopyOnWriteArrayList<>();
    private final Consumer<SimEvent> hubListener = queue::offer;
    private Timeline timeline;

    /** Starts draining events. Must be called on the FX thread. */
    public void start() {
        if (timeline != null) {
            return;
        }
        SimEventHub.addListener(hubListener);
        timeline = new Timeline(new KeyFrame(Duration.millis(DRAIN_MILLIS), e -> drain()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    /** Stops draining and unsubscribes from the hub. Safe to call from any thread. */
    public void stop() {
        SimEventHub.removeListener(hubListener);
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        queue.clear();
    }

    /**
     * Registers a batch handler. Handlers are invoked on the FX thread with a
     * chronologically ordered batch of events.
     */
    public void addHandler(Consumer<List<SimEvent>> handler) {
        handlers.addIfAbsent(handler);
    }

    public void removeHandler(Consumer<List<SimEvent>> handler) {
        handlers.remove(handler);
    }

    private void drain() {
        if (queue.isEmpty() || handlers.isEmpty()) {
            return;
        }
        List<SimEvent> batch = new ArrayList<>(Math.min(queue.size(), MAX_BATCH));
        SimEvent event;
        while (batch.size() < MAX_BATCH && (event = queue.poll()) != null) {
            batch.add(event);
        }
        if (batch.isEmpty()) {
            return;
        }
        for (Consumer<List<SimEvent>> handler : handlers) {
            try {
                handler.accept(batch);
            } catch (Throwable ignored) {
                // a failing view must not break the bridge
            }
        }
    }
}
