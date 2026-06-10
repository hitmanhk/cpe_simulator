# External Model Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move TR-098 and TR-181 model YAML files from inside the JAR to an external `models/` directory next to the JAR, so users can modify them without rebuilding.

**Architecture:** `ModelLoader` resolves a base directory from the JAR location (or `user.dir` in dev mode), then maps shortcut names `tr181`/`tr098` to `{baseDir}/models/tr181.yaml`/`tr098.yaml`. No classpath fallback — missing files cause an error exit.

**Tech Stack:** Java 21, Maven, Jackson YAML, JUnit 5

---

### Task 1: Move model YAML files out of resources

**Files:**
- Create: `models/tr181.yaml`
- Create: `models/tr098.yaml`
- Delete: `src/main/resources/models/tr181-default.yaml`
- Delete: `src/main/resources/models/tr098-default.yaml`

- [ ] **Step 1: Copy tr181-default.yaml to models/tr181.yaml**

```bash
mkdir -p models
cp src/main/resources/models/tr181-default.yaml models/tr181.yaml
```

- [ ] **Step 2: Copy tr098-default.yaml to models/tr098.yaml**

```bash
cp src/main/resources/models/tr098-default.yaml models/tr098.yaml
```

- [ ] **Step 3: Delete original resource files and directory**

```bash
rm src/main/resources/models/tr181-default.yaml src/main/resources/models/tr098-default.yaml
rmdir src/main/resources/models
```

- [ ] **Step 4: Verify files moved correctly**

```bash
ls models/
```
Expected: `tr181.yaml  tr098.yaml`
Expected: `src/main/resources/models` directory no longer exists

- [ ] **Step 5: Commit**

```bash
git add models/tr181.yaml models/tr098.yaml
git rm src/main/resources/models/tr181-default.yaml src/main/resources/models/tr098-default.yaml
git commit -m "refactor: move model YAML files from JAR resources to external models/ directory"
```

---

### Task 2: Rewrite ModelLoader to load from external files

**Files:**
- Modify: `src/main/java/com/cpe/simulator/model/ModelLoader.java`

- [ ] **Step 1: Write the failing test for resolveBaseDir**

Add a test in `ModelLoaderTest.java` that verifies the base directory resolves to a valid path containing the `models/` directory. This also validates that `load("tr181")` works after the refactor.

In `src/test/java/com/cpe/simulator/model/ModelLoaderTest.java`, rename the existing test `testLoadTr181FromClasspath` to `testLoadTr181FromExternalFile` and `testLoadTr098FromClasspath` to `testLoadTr098FromExternalFile`. The test bodies stay the same — they call `ModelLoader.load("tr181")` and `ModelLoader.load("tr098")`, which will now resolve from external files.

Also update the `testLoadUnknownModelSpec` test to verify the error message mentions the expected file path:

```java
@Test
void testLoadUnknownModelSpec() {
    RuntimeException ex = assertThrows(RuntimeException.class, () -> ModelLoader.load("nonexistent"));
    assertTrue(ex.getMessage().contains("Model file not found"),
              "Error message should mention 'Model file not found': " + ex.getMessage());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ModelLoaderTest -pl .`
Expected: FAIL — `ModelLoader` still calls `loadFromClasspath` which no longer has the YAML on classpath (deleted in Task 1)

- [ ] **Step 3: Rewrite ModelLoader**

Replace the entire `ModelLoader.java` with:

```java
package com.cpe.simulator.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModelLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static DataModel load(String modelSpec) {
        if ("tr181".equalsIgnoreCase(modelSpec)) {
            return loadFromFile(resolveModelPath("tr181.yaml"));
        } else if ("tr098".equalsIgnoreCase(modelSpec)) {
            return loadFromFile(resolveModelPath("tr098.yaml"));
        } else {
            return loadFromFile(modelSpec);
        }
    }

    public static DataModel loadFromFile(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("Model file not found: " + path.toAbsolutePath()
                    + ". Expected models/tr181.yaml or models/tr098.yaml in the same directory as the JAR file.");
        }
        try (InputStream is = Files.newInputStream(path)) {
            JsonNode root = YAML_MAPPER.readTree(is);
            return buildDataModel(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from file: " + filePath, e);
        }
    }

    private static String resolveModelPath(String modelName) {
        return resolveBaseDir().resolve("models").resolve(modelName).toString();
    }

    private static Path resolveBaseDir() {
        try {
            Path jarPath = Path.of(ModelLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isDirectory(jarPath)) {
                return Path.of(System.getProperty("user.dir"));
            }
            return jarPath.getParent();
        } catch (URISyntaxException e) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    private static DataModel buildDataModel(JsonNode root) {
        DataModel dataModel = new DataModel();
        JsonNode parameters = root.get("parameters");
        if (parameters == null || !parameters.isArray()) {
            dataModel.saveDefaults();
            return dataModel;
        }

        List<ParameterDefinition> defs = new ArrayList<>();
        for (JsonNode param : parameters) {
            ParameterDefinition def = new ParameterDefinition();
            def.path = param.get("path").asText();
            def.type = param.has("type") ? param.get("type").asText() : "xsd:string";
            def.value = param.has("value") ? param.get("value").asText() : "";
            def.writable = param.has("writable") && param.get("writable").asBoolean();
            def.notification = param.has("notification") ? param.get("notification").asInt() : 0;
            def.multiInstance = param.has("multiInstance") && param.get("multiInstance").asBoolean();
            defs.add(def);
        }

        for (ParameterDefinition def : defs) {
            ensurePath(dataModel, def);
        }

        dataModel.saveDefaults();
        return dataModel;
    }

    private static void ensurePath(DataModel dataModel, ParameterDefinition def) {
        String[] parts = def.path.split("\\.");
        ParameterNode current = dataModel.getRoot();

        for (int i = 0; i < parts.length; i++) {
            String partName = parts[i];
            boolean isLast = (i == parts.length - 1);
            String childPath = buildPath(parts, i);

            ParameterNode child = current.getChild(partName);
            if (child == null) {
                child = new ParameterNode(partName, childPath);
                current.addChild(child);
            }

            if (isLast) {
                child.setValue(new ParameterValue(def.value, def.type));
                child.setWritable(def.writable);
                child.setNotification(def.notification);
            }

            if (def.multiInstance && isInstanceNumber(partName)) {
                String parentPath = i > 0 ? buildPath(parts, i - 1) : "";
                ParameterNode parentNode = i > 0 ? dataModel.getNode(parentPath) : dataModel.getRoot();
                if (parentNode != null) {
                    parentNode.setMultiInstance(true);
                    int num = Integer.parseInt(partName);
                    if (num >= parentNode.getNextInstanceNumber()) {
                        parentNode.setNextInstanceNumber(num + 1);
                    }
                }
            }

            current = child;
        }
    }

    private static boolean isInstanceNumber(String name) {
        try {
            Integer.parseInt(name);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String buildPath(String[] parts, int upToIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= upToIndex; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static class ParameterDefinition {
        String path;
        String type;
        String value;
        boolean writable;
        int notification;
        boolean multiInstance;
    }
}
```

Key changes:
- Removed `loadFromClasspath` method
- `load("tr181")`/`load("tr098")` now call `loadFromFile(resolveModelPath(...))`
- `loadFromFile` now checks `Files.exists` before reading, throws descriptive error if missing
- Added `resolveModelPath` and `resolveBaseDir` private methods
- `resolveBaseDir`: JAR path → parent dir; directory (IDE) → `user.dir`; URISyntaxException → `user.dir`

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=ModelLoaderTest -pl .`
Expected: All tests PASS (dev mode resolves to project root which contains `models/tr181.yaml` and `models/tr098.yaml`)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cpe/simulator/model/ModelLoader.java
git commit -m "feat: ModelLoader loads models from external files next to JAR"
```

---

### Task 3: Update ModelLoaderTest test names

**Files:**
- Modify: `src/test/java/com/cpe/simulator/model/ModelLoaderTest.java`

- [ ] **Step 1: Rename tests and update error assertion**

Update `ModelLoaderTest.java` — rename `testLoadTr181FromClasspath` → `testLoadTr181FromExternalFile`, `testLoadTr098FromClasspath` → `testLoadTr098FromExternalFile`, and update `testLoadUnknownModelSpec` to check for "Model file not found":

```java
@Test
void testLoadTr181FromExternalFile() {
    DataModel model = ModelLoader.load("tr181");
    assertNotNull(model);
    assertNotNull(model.getRoot());

    ParameterValue manufacturer = model.getValue("Device.DeviceInfo.Manufacturer");
    assertNotNull(manufacturer);
    assertEquals("SimVendor", manufacturer.getValue());
    assertEquals("xsd:string", manufacturer.getType());
}

@Test
void testLoadTr098FromExternalFile() {
    DataModel model = ModelLoader.load("tr098");
    assertNotNull(model);
    assertNotNull(model.getRoot());

    ParameterValue manufacturer = model.getValue("InternetGatewayDevice.DeviceInfo.Manufacturer");
    assertNotNull(manufacturer);
    assertEquals("SimVendor", manufacturer.getValue());
}

@Test
void testLoadUnknownModelSpec() {
    RuntimeException ex = assertThrows(RuntimeException.class, () -> ModelLoader.load("nonexistent"));
    assertTrue(ex.getMessage().contains("Model file not found"),
              "Error message should mention 'Model file not found': " + ex.getMessage());
}
```

All other tests in `ModelLoaderTest` (lines 35-153) remain unchanged — they already call `ModelLoader.load("tr181")`/`ModelLoader.load("tr098")`.

- [ ] **Step 2: Run tests**

Run: `mvn test -Dtest=ModelLoaderTest -pl .`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cpe/simulator/model/ModelLoaderTest.java
git commit -m "test: rename ModelLoader tests to reflect external file loading"
```

---

### Task 4: Update FatJarVerificationTest

**Files:**
- Modify: `src/test/java/com/cpe/simulator/FatJarVerificationTest.java`

- [ ] **Step 1: Remove classpath assertions, keep ModelLoader load tests**

Remove the two classpath model tests (`testTr181ModelOnClasspath` and `testTr098ModelOnClasspath`) entirely. The `testModelLoaderCanLoadTr181` and `testModelLoaderCanLoadTr098` tests (lines 107-121) already verify models load correctly — they will now load from external files instead of classpath.

Replace the entire class with:

```java
package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import com.cpe.simulator.cli.VersionCommand;
import com.cpe.simulator.model.ModelLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class FatJarVerificationTest {

    @Test
    @Timeout(10)
    void testCpeSimulatorAppClassLoading() throws Exception {
        Class<?> clazz = Class.forName("com.cpe.simulator.CpeSimulatorApp");
        assertNotNull(clazz);
        assertNotNull(clazz.getMethod("main", String[].class));
    }

    @Test
    @Timeout(10)
    void testMainCommandInstantiation() {
        MainCommand mainCommand = new MainCommand();
        assertNotNull(mainCommand);

        CommandLine cmd = new CommandLine(mainCommand);
        assertNotNull(cmd);
        assertTrue(cmd.getSubcommands().containsKey("version"));
        assertTrue(cmd.getSubcommands().containsKey("start"));
    }

    @Test
    @Timeout(10)
    void testLogbackXmlOnClasspath() {
        var resource = getClass().getClassLoader().getResource("logback.xml");
        assertNotNull(resource, "logback.xml should be on classpath");
    }

    @Test
    @Timeout(10)
    void testApplicationYamlOnClasspath() {
        var resource = getClass().getClassLoader().getResource("application.yaml");
        assertNotNull(resource, "application.yaml should be on classpath");
    }

    @Test
    @Timeout(10)
    void testVersionCommandOutputsVersionInfo() {
        VersionCommand versionCommand = new VersionCommand();
        String[] version = versionCommand.getVersion();

        assertNotNull(version);
        assertTrue(version.length > 0);

        boolean hasVersion = false;
        boolean hasBuildTime = false;
        boolean hasJdk = false;

        for (String line : version) {
            if (line.contains("CPE Simulator")) hasVersion = true;
            if (line.contains("Build time")) hasBuildTime = true;
            if (line.contains("JDK")) hasJdk = true;
        }

        assertTrue(hasVersion, "Version output should contain simulator version");
        assertTrue(hasBuildTime, "Version output should contain build time");
        assertTrue(hasJdk, "Version output should contain JDK info");
    }

    @Test
    @Timeout(10)
    void testVersionCommandRun() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try {
            VersionCommand versionCommand = new VersionCommand();
            versionCommand.run();

            String output = baos.toString();
            assertTrue(output.contains("CPE Simulator"), "Output should contain 'CPE Simulator'");
            assertTrue(output.contains("1.0.0"), "Output should contain version number");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @Timeout(10)
    void testModelLoaderCanLoadTr181() {
        var model = ModelLoader.load("tr181");
        assertNotNull(model);
        assertNotNull(model.getRoot());
    }

    @Test
    @Timeout(10)
    void testModelLoaderCanLoadTr098() {
        var model = ModelLoader.load("tr098");
        assertNotNull(model);
        assertNotNull(model.getRoot());
    }
}
```

- [ ] **Step 2: Run test**

Run: `mvn test -Dtest=FatJarVerificationTest -pl .`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/cpe/simulator/FatJarVerificationTest.java
git commit -m "test: remove classpath model assertions from FatJarVerificationTest"
```

---

### Task 5: Update CLI --model description

**Files:**
- Modify: `src/main/java/com/cpe/simulator/cli/StartCommand.java:30`

- [ ] **Step 1: Update the --model option description**

Change line 30 in `StartCommand.java` from:

```java
@Option(names = {"--model"}, description = "Data model: tr181, tr098, or file path", defaultValue = "tr181")
```

to:

```java
@Option(names = {"--model"}, description = "Data model: tr181, tr098, or file path. Shortcut names resolve to models/ directory next to the JAR", defaultValue = "tr181")
```

- [ ] **Step 2: Run quick compile check**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cpe/simulator/cli/StartCommand.java
git commit -m "docs: update --model CLI description to mention external models/ directory"
```

---

### Task 6: Configure Maven packaging to include models/ directory

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add maven-assembly-plugin for distribution package**

The current project uses `maven-shade-plugin` to produce a fat JAR named `cpe-simulator.jar`. We need to also produce a distribution directory that includes the JAR alongside the `models/` folder.

Add the `maven-assembly-plugin` after the shade plugin in `pom.xml`. Insert after the closing `</plugin>` of the shade plugin (after line 149):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.7.1</version>
    <configuration>
        <descriptors>
            <descriptor>src/assembly/dist.xml</descriptor>
        </descriptors>
        <appendAssemblyId>false</appendAssemblyId>
    </configuration>
    <executions>
        <execution>
            <id>make-assembly</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Create assembly descriptor**

Create `src/assembly/dist.xml`:

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.2.0 http://maven.apache.org/xsd/assembly-2.2.0.xsd">
    <id>dist</id>
    <formats>
        <format>dir</format>
    </formats>
    <includeBaseDirectory>true</includeBaseDirectory>
    <baseDirectory>cpe-simulator</baseDirectory>
    <files>
        <file>
            <source>${project.build.directory}/${project.build.finalName}.jar</source>
            <outputDirectory>.</outputDirectory>
        </file>
    </files>
    <fileSets>
        <fileSet>
            <directory>models</directory>
            <outputDirectory>models</outputDirectory>
            <includes>
                <include>*.yaml</include>
            </includes>
        </fileSet>
    </fileSets>
</assembly>
```

This produces a `target/cpe-simulator/` directory containing:
- `cpe-simulator.jar`
- `models/tr181.yaml`
- `models/tr098.yaml`

- [ ] **Step 3: Run full build**

Run: `mvn clean package -pl .`
Expected: BUILD SUCCESS, `target/cpe-simulator/` directory exists with JAR and `models/`

- [ ] **Step 4: Verify distribution structure**

```bash
ls target/cpe-simulator/
ls target/cpe-simulator/models/
```
Expected: `cpe-simulator.jar` in the root, `tr181.yaml  tr098.yaml` in `models/`

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/assembly/dist.xml
git commit -m "build: add maven-assembly-plugin for distribution package with models/"
```

---

### Task 7: Full test suite verification

**Files:** None (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn test -pl .`
Expected: All tests PASS

- [ ] **Step 2: Run the full build and verify distribution**

Run: `mvn clean package -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify distribution contains all expected files**

```bash
ls -la target/cpe-simulator/
ls -la target/cpe-simulator/models/
```
Expected: JAR file and both YAML model files present

---

## Self-Review Checklist

**1. Spec coverage:**
- File location strategy (JAR path → parent dir, directory → user.dir): Task 2 `resolveBaseDir`
- Shortcut mapping `tr181` → `models/tr181.yaml`, `tr098` → `models/tr098.yaml`: Task 2 `resolveModelPath`
- Remove `loadFromClasspath`: Task 2 removes the method
- File not found → descriptive error exit: Task 2 `loadFromFile` checks `Files.exists`
- Resource file migration: Task 1
- CLI description update: Task 5
- Test adjustments (ModelLoaderTest, FatJarVerificationTest): Tasks 3, 4
- Maven packaging: Task 6

**2. Placeholder scan:** No TBD/TODO found. All steps have complete code.

**3. Type consistency:** `resolveBaseDir()` returns `Path`, `resolveModelPath` returns `String` (matches `loadFromFile` parameter type). All method signatures consistent across tasks.