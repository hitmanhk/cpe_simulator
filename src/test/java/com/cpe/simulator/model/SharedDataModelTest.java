package com.cpe.simulator.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharedDataModelTest {

    @Test
    void sharedCopiesRetainOneStructureAndSparseDeviceValues() {
        DataModel template = ModelLoader.load("tr181");
        DataModel first = template.sharedCopy();
        DataModel second = template.sharedCopy();

        ModelTemplate.resolveModel(first, context("SIM00001", "http://127.0.0.1/one"));
        ModelTemplate.resolveModel(second, context("SIM00002", "http://127.0.0.1/two"));

        assertTrue(first.isStructureShared());
        assertTrue(first.sharesStructureWith(second));
        assertTrue(first.overrideCount() > 0);
        assertTrue(first.overrideCount() < 20,
            "Only template-dependent leaves should have per-device state");
        assertEquals("SIM00001",
            first.getValue("Device.DeviceInfo.SerialNumber").getValue());
        assertEquals("SIM00002",
            second.getValue("Device.DeviceInfo.SerialNumber").getValue());
        assertEquals("${device.serial}",
            template.getValue("Device.DeviceInfo.SerialNumber").getValue());
    }

    @Test
    void valueAndAttributeChangesAreIsolatedBetweenDevices() {
        DataModel template = ModelLoader.load("tr181");
        DataModel first = template.sharedCopy();
        DataModel second = template.sharedCopy();
        String path = "Device.ManagementServer.PeriodicInformInterval";

        first.setValue(path, "120");
        first.getValue(path).setType("xsd:string");
        first.getNode(path).setNotification(2);
        first.getNode(path).setAccessList(List.of("Admin"));

        assertEquals("120", first.getValue(path).getValue());
        assertEquals("xsd:string", first.getValue(path).getType());
        assertEquals(2, first.getNode(path).getNotification());
        assertEquals(List.of("Admin"), first.getNode(path).getAccessList());
        assertNotEquals("120", second.getValue(path).getValue());
        assertEquals("xsd:unsignedInt", second.getValue(path).getType());
        assertNotEquals(2, second.getNode(path).getNotification());
        assertNotEquals(List.of("Admin"), second.getNode(path).getAccessList());
    }

    @Test
    void factoryResetRestoresResolvedPerDeviceDefaults() {
        DataModel device = ModelLoader.load("tr181").sharedCopy();
        ModelTemplate.resolveModel(device, context("SIM00123", "http://127.0.0.1/device"));
        device.saveDefaults();

        device.setValue("Device.DeviceInfo.SerialNumber", "CHANGED");
        device.setValue("Device.ManagementServer.PeriodicInformInterval", "120");
        device.factoryReset();

        assertEquals("SIM00123",
            device.getValue("Device.DeviceInfo.SerialNumber").getValue());
        assertEquals("300",
            device.getValue("Device.ManagementServer.PeriodicInformInterval").getValue());
        assertTrue(device.isStructureShared());
    }

    @Test
    void structuralMutationDetachesOnlyTheAffectedDevice() {
        DataModel template = ModelLoader.load("tr181");
        DataModel first = template.sharedCopy();
        DataModel second = template.sharedCopy();
        String parent = "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection";

        ParameterNode added = first.addInstance(parent);

        assertEquals("2", added.getName());
        assertFalse(first.isStructureShared());
        assertTrue(second.isStructureShared());
        assertNotNull(first.getNode(parent + ".2"));
        assertNull(second.getNode(parent + ".2"));
        assertNull(template.getNode(parent + ".2"));
    }

    private static ModelTemplate.TemplateContext context(String serial, String connectionUrl) {
        return new ModelTemplate.TemplateContext(
            serial, "AABBCC", "SharedCPE", "http://acs.example.com/", connectionUrl);
    }
}
