package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

/**
 * Base class for the main content views. Holds references to the shared
 * engine controller and event bridge; subclasses override lifecycle hooks to
 * start/stop polling when the engine state changes.
 */
public abstract class AbstractView extends VBox {

    protected final EngineController engineController;
    protected final UiEventBridge eventBridge;

    protected AbstractView(EngineController engineController, UiEventBridge eventBridge) {
        this.engineController = engineController;
        this.eventBridge = eventBridge;
        setSpacing(12);
        setPadding(new Insets(16));
    }

    /** Called on the FX thread when the engine reaches RUNNING. */
    public void onEngineStarted() {
    }

    /** Called on the FX thread when the engine becomes STOPPED. */
    public void onEngineStopped() {
    }

    /** Releases timers/listeners when the window closes. */
    public void dispose() {
    }
}
