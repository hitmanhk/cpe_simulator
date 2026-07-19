package com.cpe.simulator.common.event;

import com.cpe.simulator.common.SimLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimEventHubTest {

    private final List<SimEvent> received = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        SimEventHub.clearListeners();
        SimLogger.clearDeviceFilters();
    }

    @Test
    void publishesToAllListeners() {
        List<SimEvent> second = new CopyOnWriteArrayList<>();
        SimEventHub.addListener(received::add);
        SimEventHub.addListener(second::add);

        assertTrue(SimEventHub.hasListeners());
        SimEvent event = SimEvent.of(SimEvent.Category.CWMP_RPC, "dev-1", "Inform",
                java.util.Map.of("k", "v"));
        SimEventHub.publish(event);

        assertEquals(1, received.size());
        assertEquals(1, second.size());
        assertSame(event, received.get(0));
        assertEquals("dev-1", received.get(0).deviceId());
        assertEquals("v", received.get(0).attr("k"));
    }

    @Test
    void removeListenerStopsDelivery() {
        java.util.function.Consumer<SimEvent> listener = received::add;
        SimEventHub.addListener(listener);
        SimEventHub.removeListener(listener);
        assertFalse(SimEventHub.hasListeners());
        SimEventHub.publish(SimEvent.of(SimEvent.Category.XMPP_EVENT, "d", "e", java.util.Map.of()));
        assertEquals(0, received.size());
    }

    @Test
    void listenerExceptionIsIsolated() {
        AtomicInteger calls = new AtomicInteger();
        SimEventHub.addListener(e -> { throw new RuntimeException("boom"); });
        SimEventHub.addListener(e -> calls.incrementAndGet());

        assertDoesNotThrow(() -> SimEventHub.publish(
                SimEvent.of(SimEvent.Category.DEVICE_STATE, "d", "ONLINE", java.util.Map.of())));
        assertEquals(1, calls.get());
    }

    @Test
    void simLoggerCwmpRpcPublishesEvent() {
        SimEventHub.addListener(received::add);
        SimLogger.cwmpRpc("dev-9", "sess-1", "CPE_TO_ACS", "Inform", "SUCCESS", "http 200");

        assertEquals(1, received.size());
        SimEvent e = received.get(0);
        assertEquals(SimEvent.Category.CWMP_RPC, e.category());
        assertEquals("dev-9", e.deviceId());
        assertEquals("Inform", e.type());
        assertEquals("sess-1", e.attr("sessionId"));
        assertEquals("CPE_TO_ACS", e.attr("direction"));
        assertEquals("SUCCESS", e.attr("status"));
    }

    @Test
    void simLoggerConnectionStateMapsCategory() {
        SimEventHub.addListener(received::add);
        SimLogger.connectionState("dev-1", "DEVICE", "CREATED", "ONLINE", "public mode ready");
        SimLogger.connectionState("dev-1", "XMPP", "CONNECTING", "AUTHENTICATING", "sasl");

        assertEquals(2, received.size());
        assertEquals(SimEvent.Category.DEVICE_STATE, received.get(0).category());
        assertEquals("ONLINE", received.get(0).type());
        assertEquals("CREATED", received.get(0).attr("previous"));
        assertEquals(SimEvent.Category.CONNECTION_STATE, received.get(1).category());
    }

    @Test
    void simLoggerEventsBypassDeviceFilter() {
        // 日志过滤器只影响日志行，不应阻断 UI 事件流
        SimLogger.addDeviceFilter("other-device");
        SimEventHub.addListener(received::add);
        SimLogger.xmppEvent("dev-2", "TCP_CONNECTED", "remote=1.2.3.4");
        SimLogger.cwmpRpc("dev-2", "s", "ACS_TO_CPE", "GetParameterValues", "RECEIVED", "");

        assertEquals(2, received.size());
        assertEquals(SimEvent.Category.XMPP_EVENT, received.get(0).category());
        assertEquals("TCP_CONNECTED", received.get(0).type());
        assertEquals(SimEvent.Category.CWMP_RPC, received.get(1).category());
    }
}
