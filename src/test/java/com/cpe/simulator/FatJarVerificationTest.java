package com.cpe.simulator;

import com.cpe.simulator.cli.MainCommand;
import com.cpe.simulator.cli.VersionCommand;
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
    void testTr181ModelOnClasspath() {
        var resource = getClass().getClassLoader().getResource("models/tr181-default.yaml");
        assertNotNull(resource, "models/tr181-default.yaml should be on classpath");
    }

    @Test
    @Timeout(10)
    void testTr098ModelOnClasspath() {
        var resource = getClass().getClassLoader().getResource("models/tr098-default.yaml");
        assertNotNull(resource, "models/tr098-default.yaml should be on classpath");
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
        var model = com.cpe.simulator.model.ModelLoader.load("tr181");
        assertNotNull(model);
        assertNotNull(model.getRoot());
    }

    @Test
    @Timeout(10)
    void testModelLoaderCanLoadTr098() {
        var model = com.cpe.simulator.model.ModelLoader.load("tr098");
        assertNotNull(model);
        assertNotNull(model.getRoot());
    }
}
