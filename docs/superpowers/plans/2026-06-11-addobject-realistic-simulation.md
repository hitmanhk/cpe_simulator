# AddObject Realistic Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AddObject` clone instance 1's subtree so newly added instances have real, independent parameters the ACS can interact with.

**Architecture:** Modify `DataModel.addInstance()` to deep-copy instance 1's children into the new instance, rewrite all `fullPath` values to reflect the new instance number, and support instance number reuse via a `TreeSet<Integer>` on `ParameterNode`.

**Tech Stack:** Java 21, JUnit 5, Mockito

---

### Task 1: Add `availableInstanceNumbers` to `ParameterNode`

**Files:**
- Modify: `src/main/java/com/cpe/simulator/model/ParameterNode.java`
- Test: `src/test/java/com/cpe/simulator/model/DataModelTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DataModelTest.java` after the existing `testDeleteInstanceWithTrailingDot` test:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest#testDeleteInstanceMakesNumberAvailableForReuse -DfailIfNoTests=false -q`
Expected: FAIL — `addInstance` currently returns instance 3 (nextInstanceNumber was already 3), not 2.

- [ ] **Step 3: Add `availableInstanceNumbers` field and methods to `ParameterNode`**

In `ParameterNode.java`, add the import and field:

```java
import java.util.TreeSet;
```

Add field after `private int nextInstanceNumber;`:

```java
private TreeSet<Integer> availableInstanceNumbers = new TreeSet<>();
```

Add methods after `setNextInstanceNumber`:

```java
public void addAvailableInstanceNumber(int num) {
    availableInstanceNumbers.add(num);
}

public Integer pollAvailableInstanceNumber() {
    return availableInstanceNumbers.pollFirst();
}
```

Update `deepCopy()` — add after `copy.nextInstanceNumber = this.nextInstanceNumber;`:

```java
copy.availableInstanceNumbers = new TreeSet<>(this.availableInstanceNumbers);
```

- [ ] **Step 4: Update `DataModel.deleteInstance()` to recycle instance number**

Replace the `deleteInstance` method in `DataModel.java`:

```java
public void deleteInstance(String instancePath) {
    String cleanPath = instancePath.endsWith(".")
        ? instancePath.substring(0, instancePath.length() - 1)
        : instancePath;
    int lastDot = cleanPath.lastIndexOf('.');
    if (lastDot < 0) {
        throw new IllegalArgumentException("Invalid instance path: " + instancePath);
    }
    String parentPath = cleanPath.substring(0, lastDot);
    String instanceName = cleanPath.substring(lastDot + 1);
    ParameterNode parent = getNode(parentPath);
    if (parent == null) {
        throw new IllegalArgumentException("Parent not found: " + parentPath);
    }
    ParameterNode instance = parent.getChild(instanceName);
    if (instance == null) {
        throw new IllegalArgumentException("Instance not found: " + instancePath);
    }
    parent.removeChild(instanceName);
    try {
        int instanceNum = Integer.parseInt(instanceName);
        parent.addAvailableInstanceNumber(instanceNum);
    } catch (NumberFormatException ignored) {
        // non-numeric instance name, nothing to recycle
    }
}
```

- [ ] **Step 5: Update `DataModel.addInstance()` to prefer available instance numbers**

Replace the instance number allocation block in `addInstance()`. Change these two lines:

```java
int instanceNum = parent.getNextInstanceNumber();
parent.setNextInstanceNumber(instanceNum + 1);
```

To:

```java
Integer recycled = parent.pollAvailableInstanceNumber();
int instanceNum = (recycled != null) ? recycled : parent.getNextInstanceNumber();
if (recycled == null) {
    parent.setNextInstanceNumber(instanceNum + 1);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest#testDeleteInstanceMakesNumberAvailableForReuse -DfailIfNoTests=false -q`
Expected: PASS

- [ ] **Step 7: Run full DataModelTest suite to verify no regressions**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest -DfailIfNoTests=false -q`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cpe/simulator/model/ParameterNode.java src/main/java/com/cpe/simulator/model/DataModel.java src/test/java/com/cpe/simulator/model/DataModelTest.java
git commit -m "feat: add instance number reuse for AddObject/DeleteObject"
```

---

### Task 2: Clone instance 1 subtree in `addInstance()`

**Files:**
- Modify: `src/main/java/com/cpe/simulator/model/DataModel.java`
- Test: `src/test/java/com/cpe/simulator/model/DataModelTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DataModelTest.java`:

```java
@Test
void testAddInstanceClonesInstance1Children() {
    ParameterNode newInstance = model.addInstance("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
    assertNotNull(newInstance);
    assertEquals("2", newInstance.getName());

    ParameterNode nameParam = model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.Name");
    assertNotNull(nameParam);
    assertEquals("WAN", nameParam.getValue().getValue());
    assertTrue(nameParam.isWritable());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest#testAddInstanceClonesInstance1Children -DfailIfNoTests=false -q`
Expected: FAIL — `nameParam` is null because `addInstance` creates an empty shell node.

- [ ] **Step 3: Add `rewriteInstancePaths()` helper to `DataModel`**

Add private method to `DataModel.java` after `deleteInstance()`:

```java
private void rewriteInstancePaths(ParameterNode node, String oldPrefix, String newPrefix) {
    String path = node.getFullPath();
    if (path.startsWith(oldPrefix)) {
        node.setFullPath(newPrefix + path.substring(oldPrefix.length()));
    }
    for (ParameterNode child : node.getChildren().values()) {
        rewriteInstancePaths(child, oldPrefix, newPrefix);
    }
}
```

- [ ] **Step 4: Update `addInstance()` to clone instance 1's subtree**

Replace the entire `addInstance()` method in `DataModel.java`:

```java
public ParameterNode addInstance(String parentPath) {
    ParameterNode parent = getNode(parentPath);
    if (parent == null) {
        throw new IllegalArgumentException("Parent not found: " + parentPath);
    }
    if (!parent.isMultiInstance()) {
        throw new IllegalArgumentException("Not a multi-instance object: " + parentPath);
    }

    Integer recycled = parent.pollAvailableInstanceNumber();
    int instanceNum = (recycled != null) ? recycled : parent.getNextInstanceNumber();
    if (recycled == null) {
        parent.setNextInstanceNumber(instanceNum + 1);
    }

    String instanceName = String.valueOf(instanceNum);
    String instancePath = parent.getFullPath().isEmpty()
        ? instanceName
        : parent.getFullPath() + "." + instanceName;

    ParameterNode instanceNode = new ParameterNode(instanceName, instancePath);
    instanceNode.setMultiInstance(false);

    ParameterNode template = parent.getChild("1");
    if (template != null) {
        ParameterNode clonedTemplate = template.deepCopy();
        String oldPrefix = template.getFullPath();
        String newPrefix = instanceNode.getFullPath();
        for (ParameterNode child : clonedTemplate.getChildren().values()) {
            rewriteInstancePaths(child, oldPrefix, newPrefix);
            instanceNode.addChild(child);
        }
    }

    parent.addChild(instanceNode);
    return instanceNode;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest#testAddInstanceClonesInstance1Children -DfailIfNoTests=false -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cpe/simulator/model/DataModel.java
git commit -m "feat: clone instance 1 subtree when adding new instances"
```

---

### Task 3: Test cloned instance independence and multi-level nesting

**Files:**
- Test: `src/test/java/com/cpe/simulator/model/DataModelTest.java`

- [ ] **Step 1: Write independence test**

Add to `DataModelTest.java`:

```java
@Test
void testClonedInstanceIsIndependent() {
    ParameterNode newInstance = model.addInstance("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
    assertNotNull(newInstance);

    model.setValue("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.Name", "ModifiedWAN");
    assertEquals("ModifiedWAN", model.getValue("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.Name").getValue());
    assertEquals("WAN", model.getValue("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name").getValue());
}
```

- [ ] **Step 2: Write multi-level AddObject test**

Add to `DataModelTest.java`:

```java
@Test
void testMultiLevelAddObject() {
    ParameterNode wanDevice2 = model.addInstance("Device.WANDevice");
    assertNotNull(wanDevice2);
    assertEquals("2", wanDevice2.getName());

    ParameterNode wanConn = model.getNode("Device.WANDevice.2.WANConnectionDevice");
    assertNotNull(wanConn);
    assertTrue(wanConn.isMultiInstance());

    ParameterNode wanIp = model.getNode("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection");
    assertNotNull(wanIp);
    assertTrue(wanIp.isMultiInstance());

    ParameterNode nameParam = model.getNode("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name");
    assertNotNull(nameParam);
    assertEquals("WAN", nameParam.getValue().getValue());

    ParameterNode innerNew = model.addInstance("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection");
    assertNotNull(innerNew);
    assertEquals("2", innerNew.getName());

    ParameterNode innerName = model.getNode("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.2.Name");
    assertNotNull(innerName);
    assertEquals("WAN", innerName.getValue().getValue());
}
```

- [ ] **Step 3: Write fullPath correctness test**

Add to `DataModelTest.java`:

```java
@Test
void testClonedInstancePathsAreCorrect() {
    model.addInstance("Device.WANDevice");

    ParameterNode wanConn = model.getNode("Device.WANDevice.2.WANConnectionDevice");
    assertNotNull(wanConn);
    assertEquals("Device.WANDevice.2.WANConnectionDevice", wanConn.getFullPath());

    ParameterNode wanIp = model.getNode("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection");
    assertNotNull(wanIp);
    assertEquals("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection", wanIp.getFullPath());

    ParameterNode nameParam = model.getNode("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name");
    assertNotNull(nameParam);
    assertEquals("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name", nameParam.getFullPath());
}
```

- [ ] **Step 4: Write no-instance-1 graceful degradation test**

Add to `DataModelTest.java`:

```java
@Test
void testAddInstanceWithoutTemplateCreatesEmptyNode() {
    ParameterNode wanDevice = model.getNode("Device.WANDevice");
    wanDevice.removeChild("1");

    ParameterNode newInstance = model.addInstance("Device.WANDevice");
    assertNotNull(newInstance);
    assertEquals("2", newInstance.getName());
    assertTrue(newInstance.getChildren().isEmpty());
}
```

- [ ] **Step 5: Run all DataModelTest tests**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=DataModelTest -DfailIfNoTests=false -q`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/cpe/simulator/model/DataModelTest.java
git commit -m "test: add independence, multi-level, and path correctness tests for AddObject"
```

---

### Task 4: Fix existing `testAddInstance` and `RpcHandlerTest` expectations

**Files:**
- Modify: `src/test/java/com/cpe/simulator/model/DataModelTest.java`
- Modify: `src/test/java/com/cpe/simulator/cwmp/RpcHandlerTest.java`

The existing `testAddInstance` in `DataModelTest` adds an instance to `WANIPConnection` and verifies it exists, but the new instance now has cloned children. The existing assertion `assertNotNull(model.getNode("Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2"))` should still pass since the node exists. No change needed to `DataModelTest.testAddInstance`.

The `RpcHandlerTest.addObjectCreatesNewInstance` test creates a `WANDevice` node with `multiInstance=true` but **no instance 1 child**. The `addInstance()` method now clones instance 1's children, so when instance 1 doesn't exist it creates a bare node — same behavior as before. The test asserts `InstanceNumber` is `"1"` but the current `setUp` doesn't set `nextInstanceNumber`, so it defaults to 1. This should still pass. No change needed.

However, the `RpcHandlerTest.deleteObjectRemovesInstance` test calls `dataModel.addInstance("Device.WANDevice")` first, which now creates instance 1 (since `nextInstanceNumber` starts at 1 and no instance 1 exists). Then it deletes `Device.WANDevice.1.`. This should still work. No change needed.

- [ ] **Step 1: Run full RpcHandlerTest to verify no regressions**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=RpcHandlerTest -DfailIfNoTests=false -q`
Expected: All tests PASS

- [ ] **Step 2: Run full test suite**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -q`
Expected: All tests PASS

- [ ] **Step 3: Commit if any fixes were needed, otherwise skip**

Only commit if changes were required.

---

### Task 5: End-to-end handler test — AddObject with cloned data, then GetParameterValues

**Files:**
- Modify: `src/test/java/com/cpe/simulator/cwmp/RpcHandlerTest.java`

- [ ] **Step 1: Update RpcHandlerTest setUp to include instance 1 with children**

The current `RpcHandlerTest.setUp()` has `WANDevice` as multiInstance but no instance 1 with children. We need to add a test that uses a model with instance 1 populated. Add a helper method and a test after the existing tests.

Add this test method:

```java
@Test
void addObjectClonesInstance1AndGetParameterValuesReturnsClonedData() {
    DataModel fullModel = new DataModel();
    ParameterNode deviceNode = new ParameterNode("Device", "Device");

    ParameterNode wanNode = new ParameterNode("WANDevice", "Device.WANDevice");
    wanNode.setMultiInstance(true);
    wanNode.setNextInstanceNumber(2);

    ParameterNode wan1 = new ParameterNode("1", "Device.WANDevice.1");
    ParameterNode wanConn = new ParameterNode("WANConnectionDevice", "Device.WANDevice.1.WANConnectionDevice");
    wanConn.setMultiInstance(true);
    wanConn.setNextInstanceNumber(2);
    wan1.addChild(wanConn);

    ParameterNode wanConn1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1");
    ParameterNode wanIp = new ParameterNode("WANIPConnection", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
    wanIp.setMultiInstance(true);
    wanIp.setNextInstanceNumber(2);
    wanConn1.addChild(wanIp);

    ParameterNode wanIp1 = new ParameterNode("1", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1");
    ParameterNode nameParam = new ParameterNode("Name", "Device.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.Name");
    nameParam.setValue(new ParameterValue("WAN", "xsd:string"));
    nameParam.setWritable(true);
    wanIp1.addChild(nameParam);
    wanIp.addChild(wanIp1);

    wan1.addChild(wanConn);
    wanNode.addChild(wan1);
    deviceNode.addChild(wanNode);
    fullModel.getRoot().addChild(deviceNode);

    when(device.getDataModel()).thenReturn(fullModel);

    AddObjectHandler addHandler = new AddObjectHandler();
    SoapEnvelope addRequest = new SoapEnvelope();
    addRequest.setCwmpId("SIM-10");
    addRequest.setMethodName("AddObject");
    addRequest.setRawBody(buildAddObjectXml("Device.WANDevice."));

    SoapEnvelope addResponse = addHandler.handle(device, addRequest);
    assertFalse(addResponse.isFault());
    assertEquals("2", addResponse.getParameters().get("InstanceNumber"));

    GetParameterValuesHandler getHandler = new GetParameterValuesHandler();
    SoapEnvelope getRequest = new SoapEnvelope();
    getRequest.setCwmpId("SIM-11");
    getRequest.setMethodName("GetParameterValues");
    getRequest.setRawBody(buildGetParameterValuesXml("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name"));

    SoapEnvelope getResponse = getHandler.handle(device, getRequest);
    assertFalse(getResponse.isFault());
    List<SoapEnvelope.ParameterInfo> params = getResponse.getParameterList();
    assertEquals(1, params.size());
    assertEquals("Device.WANDevice.2.WANConnectionDevice.1.WANIPConnection.1.Name", params.get(0).name());
    assertEquals("WAN", params.get(0).value());
}
```

- [ ] **Step 2: Run the new test**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=RpcHandlerTest#addObjectClonesInstance1AndGetParameterValuesReturnsClonedData -DfailIfNoTests=false -q`
Expected: PASS

- [ ] **Step 3: Run full RpcHandlerTest suite**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -Dtest=RpcHandlerTest -DfailIfNoTests=false -q`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/cpe/simulator/cwmp/RpcHandlerTest.java
git commit -m "test: add end-to-end AddObject+GetParameterValues handler test"
```

---

### Task 6: Final regression check

**Files:** None — verification only

- [ ] **Step 1: Run full project test suite**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn test -pl . -q`
Expected: All tests PASS

- [ ] **Step 2: Verify build succeeds**

Run: `cd /home/iris/workspace/code/cpe_simulator && mvn package -DskipTests -q`
Expected: BUILD SUCCESS
