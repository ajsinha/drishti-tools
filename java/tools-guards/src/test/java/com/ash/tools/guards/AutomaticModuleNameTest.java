/*
 * Tools · shared, domain-free Java utilities.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.tools.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every published module jar declares its stable {@code Automatic-Module-Name}: the module's root package
 * ({@code com.ash.tools.<name>}), set per module as {@code automatic.module.name} and written into the MANIFEST by the
 * maven-jar-plugin configuration of the parent POM. The POM property is checked on every run; the jar's MANIFEST is checked
 * whenever the jars are packaged (a {@code verify} or {@code package} run).
 */
class AutomaticModuleNameTest {

    private static final String PREFIX = "com.ash.tools.";

    /** The modules that build a jar: every {@code tools-*} folder whose POM does not say {@code pom} packaging (the BOM does not). */
    static List<Path> jarModules() {
        try (Stream<Path> s = Files.list(Sources.JAVA)) {
            return s.filter(p -> p.getFileName().toString().startsWith("tools-") && Files.isRegularFile(p.resolve("pom.xml")))
                    .filter(p -> !Sources.read(p.resolve("pom.xml")).contains("<packaging>pom</packaging>"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String pkg(Path module) {
        return module.getFileName().toString().substring("tools-".length());
    }

    @Test
    void everyModulePomSetsItsRootPackageAsTheAutomaticModuleName() {
        List<String> wrong = new ArrayList<>();
        List<Path> modules = jarModules();
        assertFalse(modules.isEmpty(), "no jar modules found under " + Sources.JAVA);
        for (Path m : modules) {
            String want = PREFIX + pkg(m);
            String pom = Sources.read(m.resolve("pom.xml"));
            if (!pom.contains("<automatic.module.name>" + want + "</automatic.module.name>")) {
                wrong.add(Sources.rel(m.resolve("pom.xml")) + " does not set automatic.module.name to " + want);
            }
        }
        assertEquals(List.of(), wrong, "a module without its Automatic-Module-Name");
    }

    @Test
    void theParentPomWritesTheNameIntoEveryJarManifest() {
        String parent = Sources.read(Sources.JAVA.resolve("pom.xml"));
        assertTrue(Pattern.compile("(?s)<artifactId>maven-jar-plugin</artifactId>.*?<Automatic-Module-Name>\\$\\{automatic\\.module\\.name\\}</Automatic-Module-Name>")
                .matcher(parent).find(), "the parent POM's maven-jar-plugin does not write Automatic-Module-Name");
    }

    @Test
    void theBuiltJarsDeclareTheName() throws IOException {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Path m : jarModules()) {
            String want = PREFIX + pkg(m);
            Path target = m.resolve("target");
            if (!Files.isDirectory(target)) {
                continue;
            }
            // the main jar only: tools-<name>-<version>.jar (not the -tests.jar test-jar, not -sources.jar)
            Pattern main = Pattern.compile("tools-" + Pattern.quote(pkg(m)) + "-\\d[^/]*\\.jar");
            List<Path> jars;
            try (Stream<Path> s = Files.list(target)) {
                jars = s.filter(p -> main.matcher(p.getFileName().toString()).matches())
                        .filter(p -> !p.getFileName().toString().matches(".*-(tests|sources|javadoc)\\.jar")).toList();
            }
            for (Path jar : jars) {
                checked++;
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String got = jf.getManifest() == null ? null : jf.getManifest().getMainAttributes().getValue("Automatic-Module-Name");
                    if (!want.equals(got)) {
                        wrong.add(Sources.rel(jar) + " has Automatic-Module-Name " + got + ", want " + want);
                    }
                }
            }
        }
        assertTrue(checked > 0, "no module jars are packaged: run the build with package or verify");
        assertEquals(List.of(), wrong, "a module jar without its Automatic-Module-Name");
    }
}
