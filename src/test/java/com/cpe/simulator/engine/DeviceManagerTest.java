package com.cpe.simulator.engine;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.model.DataModel;
import com.cpe.simulator.model.ModelLoader;
import com.cpe.simulator.model.ParameterValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeviceManagerTest {

    private AppConfig config;
    private DataModel templateModel;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.setCount(10);
        config.setOui("AABBCC");
        config.setProductClass("TestCPE");
        config.setSerialPrefix("TST");
        config.setAcsUrl("http://localhost:7547/");
        config.setConnReqHost("127.0.0.1");
        config.setConnReqPort(7547);
        config.setMaxSessions(100);
        config.setSessionTimeout(30);

        templateModel = new DataModel();
        ParameterValue snValue = new ParameterValue("${device.serial}", "xsd:string");
        ParameterValue ouiValue = new ParameterValue("${device.oui}", "xsd:string");
        ParameterValue pcValue = new ParameterValue("${device.productClass}", "xsd:string");

        com.cpe.simulator.model.ParameterNode root = templateModel.getRoot();
        com.cpe.simulator.model.ParameterNode device = new com.cpe.simulator.model.ParameterNode("Device", "Device");
        root.addChild(device);
        com.cpe.simulator.model.ParameterNode deviceInfo = new com.cpe.simulator.model.ParameterNode("DeviceInfo", "Device.DeviceInfo");
        device.addChild(deviceInfo);

        com.cpe.simulator.model.ParameterNode snNode = new com.cpe.simulator.model.ParameterNode("SerialNumber", "Device.DeviceInfo.SerialNumber");
        snNode.setValue(snValue);
        deviceInfo.addChild(snNode);

        com.cpe.simulator.model.ParameterNode ouiNode = new com.cpe.simulator.model.ParameterNode("OUI", "Device.DeviceInfo.OUI");
        ouiNode.setValue(ouiValue);
        deviceInfo.addChild(ouiNode);

        com.cpe.simulator.model.ParameterNode pcNode = new com.cpe.simulator.model.ParameterNode("ProductClass", "Device.DeviceInfo.ProductClass");
        pcNode.setValue(pcValue);
        deviceInfo.addChild(pcNode);

        com.cpe.simulator.model.ParameterNode managementServer = new com.cpe.simulator.model.ParameterNode(
            "ManagementServer", "Device.ManagementServer");
        device.addChild(managementServer);
        com.cpe.simulator.model.ParameterNode connReqUrl = new com.cpe.simulator.model.ParameterNode(
            "ConnectionRequestURL", "Device.ManagementServer.ConnectionRequestURL");
        connReqUrl.setValue(new ParameterValue("${conn.req.url}", "xsd:string"));
        managementServer.addChild(connReqUrl);

        templateModel.saveDefaults();
    }

    @Test
    void testCreateDevicesWithUniqueSerialNumbers() {
        DeviceManager manager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = manager.createDevices(10);

        assertEquals(10, devices.size());

        Set<String> serialNumbers = new HashSet<>();
        Set<String> deviceIds = new HashSet<>();
        for (CpeDevice device : devices) {
            assertTrue(serialNumbers.add(device.getSerialNumber()),
                "Duplicate serial number: " + device.getSerialNumber());
            assertTrue(deviceIds.add(device.getDeviceId()),
                "Duplicate device ID: " + device.getDeviceId());
        }

        manager.shutdown();
    }

    @Test
    void testEachDeviceHasIndependentDataModel() {
        DeviceManager manager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = manager.createDevices(2);

        CpeDevice device1 = devices.get(0);
        CpeDevice device2 = devices.get(1);

        assertNotSame(device1.getDataModel(), device2.getDataModel());

        device1.getDataModel().setValue("Device.DeviceInfo.SerialNumber", "MODIFIED");
        ParameterValue val2 = device2.getDataModel().getValue("Device.DeviceInfo.SerialNumber");
        assertNotEquals("MODIFIED", val2.getValue());

        manager.shutdown();
    }

    @Test
    void testTemplateVariablesAreResolved() {
        DeviceManager manager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = manager.createDevices(1);

        CpeDevice device = devices.get(0);
        DataModel dm = device.getDataModel();

        ParameterValue snValue = dm.getValue("Device.DeviceInfo.SerialNumber");
        assertNotNull(snValue);
        assertFalse(snValue.getValue().contains("${"), "Template variable not resolved: " + snValue.getValue());
        assertTrue(snValue.getValue().startsWith("TST"));

        ParameterValue ouiValue = dm.getValue("Device.DeviceInfo.OUI");
        assertNotNull(ouiValue);
        assertEquals("AABBCC", ouiValue.getValue());

        manager.shutdown();
    }

    @Test
    void testAddDevicesIncrementsCount() {
        DeviceManager manager = new DeviceManager(config, templateModel);
        manager.createDevices(5);
        assertEquals(5, manager.getDeviceCount());

        manager.addDevices(3);
        assertEquals(8, manager.getDeviceCount());

        manager.shutdown();
    }

    @Test
    void testRemoveDevice() {
        DeviceManager manager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = manager.createDevices(5);
        assertEquals(5, manager.getDeviceCount());

        String deviceId = devices.get(0).getDeviceId();
        manager.removeDevice(deviceId);
        assertEquals(4, manager.getDeviceCount());
        assertNull(manager.getDevice(deviceId));

        manager.shutdown();
    }

    @Test
    void testSerialStartIndex() {
        config.setSerialStart(100);
        DeviceManager manager = new DeviceManager(config, templateModel);
        List<CpeDevice> devices = manager.createDevices(3);

        assertEquals("TST00100", devices.get(0).getSerialNumber());
        assertEquals("TST00101", devices.get(1).getSerialNumber());
        assertEquals("TST00102", devices.get(2).getSerialNumber());

        manager.shutdown();
    }

    @Test
    void testSerialStartWithAddDevices() {
        config.setSerialStart(100);
        config.setCount(5);
        DeviceManager manager = new DeviceManager(config, templateModel);

        List<CpeDevice> initialDevices = manager.createDevices(5);
        assertEquals("TST00100", initialDevices.get(0).getSerialNumber());
        assertEquals("TST00104", initialDevices.get(4).getSerialNumber());

        manager.addDevices(3);
        assertEquals(8, manager.getDeviceCount());

        CpeDevice added0 = manager.getDevices().get(5);
        assertEquals("TST00105", added0.getSerialNumber());

        manager.shutdown();
    }

    @Test
    void testConnectionRequestUrlUsesInstanceLocalAddress() {
        config.setConnReqHost("0.0.0.0");
        config.setLocalAddress("192.0.2.11");
        DeviceManager manager = new DeviceManager(config, templateModel);

        CpeDevice device = manager.createDevices(1).get(0);

        ParameterValue connReqUrl = device.getDataModel()
            .getValue("Device.ManagementServer.ConnectionRequestURL");
        assertNotNull(connReqUrl);
        assertEquals("http://192.0.2.11:7547/connReq/" + device.getDeviceId(), connReqUrl.getValue());

        manager.shutdown();
    }
}
