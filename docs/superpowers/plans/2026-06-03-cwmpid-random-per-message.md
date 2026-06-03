# cwmpId Random Per-Message Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change cwmpId from session-level sequential counter to per-message random UUID (hyphens removed).

**Architecture:** Remove cwmpId from CwmpSession. Each CPE-initiated SOAP message generates its own cwmpId via `IdGenerator.cwmpId()`. CPE responses to ACS requests continue echoing the ACS's cwmpId (no change needed).

**Tech Stack:** Java 21, JUnit 5, Mockito

---

### Task 1: Change IdGenerator.cwmpId() to random UUID

**Files:**
- Modify: `src/main/java/com/cpe/simulator/common/IdGenerator.java:8,18-20`

- [ ] **Step 1: Remove AtomicLong counter and change cwmpId() implementation**

In `src/main/java/com/cpe/simulator/common/IdGenerator.java`, remove the counter field and update the method:

```java
package com.cpe.simulator.common;

import java.util.UUID;

public class IdGenerator {

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String serialNumber(String prefix, int index) {
        return prefix + String.format("%05d", index);
    }

    public static String cwmpId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String deviceId(String oui, String productClass, String serialNumber) {
        return oui + "-" + productClass + "-" + serialNumber;
    }

    public static String randomIp() {
        return String.format("10.%d.%d.%d",
            (int)(Math.random() * 256),
            (int)(Math.random() * 256),
            (int)(Math.random() * 254) + 1);
    }
}
```

- [ ] **Step 2: Run tests to verify nothing breaks**

Run: `mvn test`
Expected: All tests pass (cwmpId format is not asserted in existing tests)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/common/IdGenerator.java
git commit -m "refactor: change cwmpId to random UUID without hyphens"
```

---

### Task 2: Remove cwmpId from CwmpSession

**Files:**
- Modify: `src/main/java/com/cpe/simulator/cwmp/CwmpSession.java:18,37,95,111-113,151-153`

- [ ] **Step 1: Remove cwmpId field and related methods**

In `src/main/java/com/cpe/simulator/cwmp/CwmpSession.java`:

1. Remove the import: `import com.cpe.simulator.common.IdGenerator;`
2. Remove field: `private String cwmpId;`
3. In `startSession()`, remove: `this.cwmpId = IdGenerator.cwmpId();`
4. In `reset()`, remove: `this.cwmpId = null;`
5. Remove method: `public String getCwmpId() { return cwmpId; }`
6. Remove method: `public void setCwmpId(String cwmpId) { this.cwmpId = cwmpId; }`

- [ ] **Step 2: Run tests to verify compilation and behavior**

Run: `mvn test`
Expected: Compilation error in `CpeDevice.java` (expected — fixed in Task 3)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/cwmp/CwmpSession.java
git commit -m "refactor: remove cwmpId from CwmpSession"
```

---

### Task 3: Update CpeDevice and InformHandler to generate per-message cwmpId

**Files:**
- Modify: `src/main/java/com/cpe/simulator/engine/CpeDevice.java:193,217`
- Modify: `src/main/java/com/cpe/simulator/cwmp/InformHandler.java:50-53`

- [ ] **Step 1: Update InformHandler to generate cwmpId directly**

In `src/main/java/com/cpe/simulator/cwmp/InformHandler.java`, replace lines 50-53:

```java
        String cwmpId = IdGenerator.cwmpId();
```

(Remove the `device.getSession().getCwmpId()` call and null check — just generate a new one.)

- [ ] **Step 2: Update CpeDevice.processAcsRequests() fault response**

In `src/main/java/com/cpe/simulator/engine/CpeDevice.java`, line 193, change:

```java
                String faultXml = soapBuilder.buildFault(9000, "Method not supported: " + methodName, request.getCwmpId());
```

(Use `request.getCwmpId()` instead of `session.getCwmpId()` — echo the ACS's cwmpId in fault responses.)

- [ ] **Step 3: Update CpeDevice.sendEmptyRequest() to generate cwmpId**

In `src/main/java/com/cpe/simulator/engine/CpeDevice.java`, line 217, change:

```java
        String emptyXml = soapBuilder.buildEmptyRequest(IdGenerator.cwmpId());
```

- [ ] **Step 4: Run tests to verify everything compiles and passes**

Run: `mvn test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cpe/simulator/engine/CpeDevice.java src/main/java/com/cpe/simulator/cwmp/InformHandler.java
git commit -m "refactor: generate per-message cwmpId in CpeDevice and InformHandler"
```
