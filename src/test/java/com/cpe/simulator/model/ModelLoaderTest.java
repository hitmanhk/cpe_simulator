package com.cpe.simulator.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelLoaderTest {

    @Test
    void testLoadTr181FromClasspath() {
        DataModel model = ModelLoader.load("tr181");
        assertNotNull(model);
        assertNotNull(model.getRoot());

        ParameterValue manufacturer = model.getValue("Device.DeviceInfo.Manufacturer");
        assertNotNull(manufacturer);
        assertEquals("SimVendor", manufacturer.getValue());
        assertEquals("xsd:string", manufacturer.getType());
    }

    @Test
    void testLoadTr098FromClasspath() {
        DataModel model = ModelLoader.load("tr098");
        assertNotNull(model);
        assertNotNull(model.getRoot());

        ParameterValue manufacturer = model.getValue("InternetGatewayDevice.DeviceInfo.Manufacturer");
        assertNotNull(manufacturer);
        assertEquals("SimVendor", manufacturer.getValue());
    }

    @Test
    void testTr181ParameterTreeStructure() {
        DataModel model = ModelLoader.load("tr181");

        ParameterNode device = model.getNode("Device");
        assertNotNull(device);
        assertFalse(device.isLeaf());

        ParameterNode deviceInfo = model.getNode("Device.DeviceInfo");
        assertNotNull(deviceInfo);
        assertFalse(deviceInfo.isLeaf());

        ParameterNode mgmt = model.getNode("Device.ManagementServer");
        assertNotNull(mgmt);
        assertFalse(mgmt.isLeaf());

        ParameterNode swVersion = model.getNode("Device.DeviceInfo.SoftwareVersion");
        assertNotNull(swVersion);
        assertTrue(swVersion.isLeaf());
        assertTrue(swVersion.isWritable() == false);

        ParameterValue uptime = model.getValue("Device.DeviceInfo.UpTime");
        assertNotNull(uptime);
        assertEquals("xsd:unsignedInt", uptime.getType());
        assertEquals("0", uptime.getValue());

        ParameterValue periodicInform = model.getValue("Device.ManagementServer.PeriodicInformEnable");
        assertNotNull(periodicInform);
        assertEquals("xsd:boolean", periodicInform.getType());
        assertEquals("true", periodicInform.getValue());
    }

    @Test
    void testTr181MultiInstanceNodes() {
        DataModel model = ModelLoader.load("tr181");

        ParameterNode wanDevice = model.getNode("Device.WANDevice");
        assertNotNull(wanDevice);
        assertTrue(wanDevice.isMultiInstance());

        ParameterNode wanConn = model.getNode("Device.WANDevice.1.WANConnectionDevice");
        assertNotNull(wanConn);
        assertTrue(wanConn.isMultiInstance());

        ParameterNode wanIp = model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        assertNotNull(wanIp);
        assertTrue(wanIp.isMultiInstance());
    }

    @Test
    void testTr181WanParameters() {
        DataModel model = ModelLoader.load("tr181");

        ParameterValue name = model.getValue("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name");
        assertNotNull(name);
        assertEquals("WAN", name.getValue());
        assertTrue(model.isWritable("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name"));

        ParameterValue subnet = model.getValue("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.SubnetMask");
        assertNotNull(subnet);
        assertEquals("255.255.255.0", subnet.getValue());
    }

    @Test
    void testTr098ParameterTreeStructure() {
        DataModel model = ModelLoader.load("tr098");

        ParameterNode igd = model.getNode("InternetGatewayDevice");
        assertNotNull(igd);

        ParameterNode deviceInfo = model.getNode("InternetGatewayDevice.DeviceInfo");
        assertNotNull(deviceInfo);

        ParameterNode mgmt = model.getNode("InternetGatewayDevice.ManagementServer");
        assertNotNull(mgmt);

        ParameterValue url = model.getValue("InternetGatewayDevice.ManagementServer.URL");
        assertNotNull(url);
        assertTrue(model.isWritable("InternetGatewayDevice.ManagementServer.URL"));
    }

    @Test
    void testTr098MultiInstanceNodes() {
        DataModel model = ModelLoader.load("tr098");

        ParameterNode wanDevice = model.getNode("InternetGatewayDevice.WANDevice");
        assertNotNull(wanDevice);
        assertTrue(wanDevice.isMultiInstance());
    }

    @Test
    void testGetParameterNamesAfterLoad() {
        DataModel model = ModelLoader.load("tr181");

        List<String> deviceInfoParams = model.getParameterNames("Device.DeviceInfo", true);
        assertFalse(deviceInfoParams.isEmpty());
        assertTrue(deviceInfoParams.contains("Device.DeviceInfo.Manufacturer"));
        assertTrue(deviceInfoParams.contains("Device.DeviceInfo.SoftwareVersion"));
    }

    @Test
    void testFactoryResetAfterLoad() {
        DataModel model = ModelLoader.load("tr181");

        model.setValue("Device.ManagementServer.URL", "http://changed.example.com");
        assertEquals("http://changed.example.com", model.getValue("Device.ManagementServer.URL").getValue());

        model.factoryReset();
        assertEquals("${acs.url}", model.getValue("Device.ManagementServer.URL").getValue());
    }

    @Test
    void testDeepCopyAfterLoad() {
        DataModel model = ModelLoader.load("tr181");
        DataModel copy = model.deepCopy();

        copy.setValue("Device.DeviceInfo.Manufacturer", "Changed");
        assertEquals("Changed", copy.getValue("Device.DeviceInfo.Manufacturer").getValue());
        assertEquals("SimVendor", model.getValue("Device.DeviceInfo.Manufacturer").getValue());
    }

    @Test
    void testLoadUnknownModelSpec() {
        assertThrows(RuntimeException.class, () -> ModelLoader.load("nonexistent"));
    }
}
