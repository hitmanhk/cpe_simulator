package com.cpe.simulator.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataModelTest {

    private DataModel model;

    @BeforeEach
    void setUp() {
        model = new DataModel();
        ParameterNode device = new ParameterNode("Device", "Device");
        model.getRoot().addChild(device);

        ParameterNode deviceInfo = new ParameterNode("DeviceInfo", "Device.DeviceInfo");
        device.addChild(deviceInfo);

        ParameterNode manufacturer = new ParameterNode("Manufacturer", "Device.DeviceInfo.Manufacturer");
        manufacturer.setValue(new ParameterValue("SimVendor", "xsd:string"));
        manufacturer.setWritable(false);
        deviceInfo.addChild(manufacturer);

        ParameterNode softwareVersion = new ParameterNode("SoftwareVersion", "Device.DeviceInfo.SoftwareVersion");
        softwareVersion.setValue(new ParameterValue("1.0.0", "xsd:string"));
        softwareVersion.setWritable(true);
        deviceInfo.addChild(softwareVersion);

        ParameterNode mgmt = new ParameterNode("ManagementServer", "Device.ManagementServer");
        device.addChild(mgmt);

        ParameterNode url = new ParameterNode("URL", "Device.ManagementServer.URL");
        url.setValue(new ParameterValue("http://acs.example.com", "xsd:string"));
        url.setWritable(true);
        mgmt.addChild(url);

        ParameterNode wanDevice = new ParameterNode("WANDevice", "Device.WANDevice");
        wanDevice.setMultiInstance(true);
        wanDevice.setNextInstanceNumber(2);
        device.addChild(wanDevice);

        ParameterNode wanDevice1 = new ParameterNode("1", "Device.WANDevice.1");
        wanDevice.addChild(wanDevice1);

        ParameterNode wanConn = new ParameterNode("WANConnectionDevice", "Device.WANDevice.1.WANConnectionDevice");
        wanConn.setMultiInstance(true);
        wanConn.setNextInstanceNumber(2);
        wanDevice1.addChild(wanConn);

        ParameterNode wanConn1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1");
        wanConn.addChild(wanConn1);

        ParameterNode wanIp = new ParameterNode("WANIPConnection", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        wanIp.setMultiInstance(true);
        wanIp.setNextInstanceNumber(2);
        wanConn1.addChild(wanIp);

        ParameterNode wanIp1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1");
        wanIp.addChild(wanIp1);

        ParameterNode name = new ParameterNode("Name", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name");
        name.setValue(new ParameterValue("WAN", "xsd:string"));
        name.setWritable(true);
        wanIp1.addChild(name);

        model.saveDefaults();
    }

    @Test
    void testGetValue() {
        ParameterValue val = model.getValue("Device.DeviceInfo.Manufacturer");
        assertNotNull(val);
        assertEquals("SimVendor", val.getValue());
        assertEquals("xsd:string", val.getType());
    }

    @Test
    void testGetValueNonExistent() {
        assertNull(model.getValue("Device.NonExistent.Param"));
    }

    @Test
    void testSetValue() {
        model.setValue("Device.DeviceInfo.SoftwareVersion", "2.0.0");
        assertEquals("2.0.0", model.getValue("Device.DeviceInfo.SoftwareVersion").getValue());
    }

    @Test
    void testSetValueNonExistent() {
        assertThrows(IllegalArgumentException.class, () ->
            model.setValue("Device.NonExistent", "value"));
    }

    @Test
    void testIsWritable() {
        assertTrue(model.isWritable("Device.DeviceInfo.SoftwareVersion"));
        assertFalse(model.isWritable("Device.DeviceInfo.Manufacturer"));
        assertFalse(model.isWritable("Device.NonExistent"));
    }

    @Test
    void testGetParameterNamesNextLevelTrue() {
        List<String> names = model.getParameterNames("Device.DeviceInfo", true);
        assertEquals(2, names.size());
        assertTrue(names.contains("Device.DeviceInfo.Manufacturer"));
        assertTrue(names.contains("Device.DeviceInfo.SoftwareVersion"));
    }

    @Test
    void testGetParameterNamesNextLevelFalse() {
        List<String> names = model.getParameterNames("Device.DeviceInfo", false);
        assertEquals(2, names.size());
        assertTrue(names.contains("Device.DeviceInfo.Manufacturer"));
        assertTrue(names.contains("Device.DeviceInfo.SoftwareVersion"));
    }

    @Test
    void testGetParameterNamesWithContainers() {
        List<String> names = model.getParameterNames("Device", true);
        assertTrue(names.contains("Device.DeviceInfo."));
        assertTrue(names.contains("Device.ManagementServer."));
        assertTrue(names.contains("Device.WANDevice."));
    }

    @Test
    void testGetParameterNamesAllDescendants() {
        List<String> names = model.getParameterNames("Device", false);
        assertTrue(names.size() >= 4);
        assertTrue(names.contains("Device.DeviceInfo.Manufacturer"));
        assertTrue(names.contains("Device.ManagementServer.URL"));
    }

    @Test
    void testGetNode() {
        ParameterNode node = model.getNode("Device.DeviceInfo.Manufacturer");
        assertNotNull(node);
        assertEquals("Manufacturer", node.getName());
        assertTrue(node.isLeaf());
    }

    @Test
    void testGetNodeWithTrailingDot() {
        ParameterNode node = model.getNode("Device.DeviceInfo.");
        assertNotNull(node);
        assertEquals("DeviceInfo", node.getName());
        assertFalse(node.isLeaf());
    }

    @Test
    void testAddInstance() {
        ParameterNode wanIp = model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        assertNotNull(wanIp);
        assertTrue(wanIp.isMultiInstance());

        ParameterNode newInstance = model.addInstance("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        assertNotNull(newInstance);
        assertEquals("2", newInstance.getName());
        assertEquals("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2", newInstance.getFullPath());

        assertNotNull(model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2"));
    }

    @Test
    void testAddInstanceNonMultiInstance() {
        assertThrows(IllegalArgumentException.class, () ->
            model.addInstance("Device.DeviceInfo"));
    }

    @Test
    void testDeleteInstance() {
        model.deleteInstance("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1");
        assertNull(model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1"));
    }

    @Test
    void testDeleteInstanceWithTrailingDot() {
        model.deleteInstance("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.");
        assertNull(model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1"));
    }

    @Test
    void testDeleteInstanceMakesNumberAvailableForReuse() {
        ParameterNode wanDevice = model.getNode("Device.WANDevice");
        assertNotNull(wanDevice);

        model.addInstance("Device.WANDevice");
        model.deleteInstance("Device.WANDevice.2");

        ParameterNode instance1 = model.getNode("Device.WANDevice.1");
        assertNotNull(instance1);

        ParameterNode reused = model.addInstance("Device.WANDevice");
        assertEquals("2", reused.getName());
    }

    @Test
    void testDeepCopyProducesIndependentCopy() {
        DataModel copy = model.deepCopy();
        assertNotNull(copy);

        assertEquals("SimVendor", copy.getValue("Device.DeviceInfo.Manufacturer").getValue());

        copy.setValue("Device.DeviceInfo.Manufacturer", "Changed");
        assertEquals("Changed", copy.getValue("Device.DeviceInfo.Manufacturer").getValue());
        assertEquals("SimVendor", model.getValue("Device.DeviceInfo.Manufacturer").getValue());
    }

    @Test
    void testFactoryResetRestoresDefaults() {
        model.setValue("Device.DeviceInfo.SoftwareVersion", "2.0.0");
        assertEquals("2.0.0", model.getValue("Device.DeviceInfo.SoftwareVersion").getValue());

        model.factoryReset();
        assertEquals("1.0.0", model.getValue("Device.DeviceInfo.SoftwareVersion").getValue());
    }

    @Test
    void testGetParameterValues() {
        List<ParameterNode> nodes = model.getParameterValues(
            List.of("Device.DeviceInfo.Manufacturer", "Device.ManagementServer.URL"));
        assertEquals(2, nodes.size());
    }

    @Test
    void testGetRootNameReturnsDeviceForTr181() {
        assertEquals("Device", model.getRootName());
    }

    @Test
    void testGetRootNameReturnsIgdForTr098() {
        DataModel tr098Model = new DataModel();
        ParameterNode igd = new ParameterNode("InternetGatewayDevice", "InternetGatewayDevice");
        tr098Model.getRoot().addChild(igd);
        ParameterNode di = new ParameterNode("DeviceInfo", "InternetGatewayDevice.DeviceInfo");
        igd.addChild(di);
        ParameterNode mfg = new ParameterNode("Manufacturer", "InternetGatewayDevice.DeviceInfo.Manufacturer");
        mfg.setValue(new ParameterValue("IGDMfg", "xsd:string"));
        di.addChild(mfg);
        assertEquals("InternetGatewayDevice", tr098Model.getRootName());
    }

    @Test
    void testGetRootNameReturnsEmptyForNoChildren() {
        DataModel empty = new DataModel();
        assertEquals("", empty.getRootName());
    }

    @Test
    void testGetRootNameReturnsEmptyForMultiRoot() {
        DataModel multi = new DataModel();
        ParameterNode a = new ParameterNode("Device", "Device");
        ParameterNode b = new ParameterNode("InternetGatewayDevice", "InternetGatewayDevice");
        multi.getRoot().addChild(a);
        multi.getRoot().addChild(b);
        assertEquals("", multi.getRootName());
    }

    @Test
    void testResolvePathTr181() {
        assertEquals("Device.DeviceInfo.Manufacturer", model.resolvePath("DeviceInfo.Manufacturer"));
    }

    @Test
    void testResolvePathTr098() {
        DataModel tr098 = new DataModel();
        ParameterNode igd = new ParameterNode("InternetGatewayDevice", "InternetGatewayDevice");
        tr098.getRoot().addChild(igd);
        assertEquals("InternetGatewayDevice.DeviceInfo.Manufacturer", tr098.resolvePath("DeviceInfo.Manufacturer"));
    }

    @Test
    void testResolvePathEmptyRootName() {
        DataModel empty = new DataModel();
        assertEquals("DeviceInfo.Manufacturer", empty.resolvePath("DeviceInfo.Manufacturer"));
    }

    @Test
    void testGetRelativeValueTr181() {
        ParameterValue val = model.getRelativeValue("DeviceInfo.Manufacturer");
        assertNotNull(val);
        assertEquals("SimVendor", val.getValue());
    }

    @Test
    void testGetRelativeValueTr098() {
        DataModel tr098 = new DataModel();
        ParameterNode igd = new ParameterNode("InternetGatewayDevice", "InternetGatewayDevice");
        tr098.getRoot().addChild(igd);
        ParameterNode di = new ParameterNode("DeviceInfo", "InternetGatewayDevice.DeviceInfo");
        igd.addChild(di);
        ParameterNode mfg = new ParameterNode("Manufacturer", "InternetGatewayDevice.DeviceInfo.Manufacturer");
        mfg.setValue(new ParameterValue("IGDMfg", "xsd:string"));
        di.addChild(mfg);
        ParameterValue val = tr098.getRelativeValue("DeviceInfo.Manufacturer");
        assertNotNull(val);
        assertEquals("IGDMfg", val.getValue());
    }

    @Test
    void testGetRelativeValueNonExistent() {
        assertNull(model.getRelativeValue("DeviceInfo.NonExistent"));
    }

    @Test
    void testSetRelativeValueTr181() {
        model.setRelativeValue("DeviceInfo.SoftwareVersion", "3.0.0");
        assertEquals("3.0.0", model.getValue("Device.DeviceInfo.SoftwareVersion").getValue());
    }

    @Test
    void testSetRelativeValueTr098() {
        DataModel tr098 = new DataModel();
        ParameterNode igd = new ParameterNode("InternetGatewayDevice", "InternetGatewayDevice");
        tr098.getRoot().addChild(igd);
        ParameterNode ms = new ParameterNode("ManagementServer", "InternetGatewayDevice.ManagementServer");
        igd.addChild(ms);
        ParameterNode url = new ParameterNode("ConnectionRequestURL", "InternetGatewayDevice.ManagementServer.ConnectionRequestURL");
        url.setValue(new ParameterValue("", "xsd:string"));
        ms.addChild(url);
        tr098.setRelativeValue("ManagementServer.ConnectionRequestURL", "http://example.com/connReq");
        assertEquals("http://example.com/connReq", tr098.getValue("InternetGatewayDevice.ManagementServer.ConnectionRequestURL").getValue());
    }

    @Test
    void testSetRelativeValueNonExistent() {
        assertThrows(IllegalArgumentException.class, () ->
            model.setRelativeValue("ManagementServer.NonExistent", "value"));
    }
}
