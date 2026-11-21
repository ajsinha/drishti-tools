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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The public API of every published module, additive only (TL-12): {@code java/<module>/api-surface.txt} lists every public and
 * protected member of every public class of the module's package, one sorted line each, built from the compiled classes. A removed
 * or changed line fails: removing API needs a major version, so the newest tools version always serves every consumer. An added line
 * fails until the file is rewritten in the same commit, so review sees it:
 * {@code ./mvnw -o verify -pl tools-guards -am -Dtools.api.surface.write=true} (a removal is written only with
 * {@code -Dtools.api.surface.major=true}, for a new major version).
 */
class ApiSurfaceTest {

    static Path baseline(Path module) {
        return module.resolve("api-surface.txt");
    }

    static List<String> surface(Path module) throws Exception {
        String pkg = "com.ash.tools." + module.getFileName().toString().substring("tools-".length());
        Path dir = module.resolve("target/classes").resolve(pkg.replace('.', '/'));
        if (!Files.isDirectory(dir)) {
            throw new AssertionError(dir + " is missing: build the modules first");
        }
        TreeSet<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".class")).sorted().toList()) {
                String n = f.getFileName().toString();
                if (n.contains("$") || n.equals("package-info.class")) {
                    continue;                                       // nested classes come with their owner
                }
                describe(Class.forName(pkg + "." + n.substring(0, n.length() - 6), false, ApiSurfaceTest.class.getClassLoader()), out);
            }
        }
        return new ArrayList<>(out);
    }

    static boolean visible(int mod) {
        return Modifier.isPublic(mod) || Modifier.isProtected(mod);
    }

    static void describe(Class<?> c, Set<String> out) {
        if (!visible(c.getModifiers())) {
            return;
        }
        String owner = c.getName();
        StringBuilder head = new StringBuilder("class " + c.toGenericString());
        if (c.getGenericSuperclass() != null && c.getSuperclass() != Object.class && !c.isEnum() && !c.isRecord()) {
            head.append(" extends ").append(c.getGenericSuperclass().getTypeName());
        }
        for (java.lang.reflect.Type i : c.getGenericInterfaces()) {
            head.append(" implements ").append(i.getTypeName());
        }
        out.add(head.toString());
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (visible(k.getModifiers()) && !k.isSynthetic()) {
                out.add(owner + " :: " + k.toGenericString());
            }
        }
        for (Method m : c.getDeclaredMethods()) {
            if (visible(m.getModifiers()) && !m.isSynthetic() && !m.isBridge()) {
                out.add(owner + " :: " + m.toGenericString());
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (visible(f.getModifiers()) && !f.isSynthetic()) {
                out.add(owner + " :: " + f.toGenericString());
            }
        }
        for (Class<?> n : c.getDeclaredClasses()) {
            describe(n, out);
        }
    }

    private static List<String> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(l -> !l.startsWith("#") && !l.isBlank()).toList();
    }

    @Test
    void everyModulesPublicApiIsItsCommittedBaselineAndOnlyGrows() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path m : Sources.modules()) {
            List<String> now = surface(m);
            assertFalse(now.isEmpty(), m + ": no public API found");
            List<String> was = read(baseline(m));
            List<String> removed = new ArrayList<>(was);
            removed.removeAll(now);
            List<String> added = new ArrayList<>(now);
            added.removeAll(was);
            boolean write = Boolean.getBoolean("tools.api.surface.write");
            boolean major = Boolean.getBoolean("tools.api.surface.major");
            if (write && (removed.isEmpty() || major)) {
                List<String> file = new ArrayList<>();
                file.add("# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE.");
                file.add("# Generated by ApiSurfaceTest (tools-guards): the public and protected API of " + m.getFileName()
                        + ". Additive only: a removed line needs a major version. Review the diff; do not edit.");
                file.addAll(now);
                Files.write(baseline(m), file, StandardCharsets.UTF_8);
                continue;
            }
            removed.forEach(r -> problems.add(m.getFileName() + " removed or changed (needs a major version): " + r));
            added.forEach(a -> problems.add(m.getFileName() + " added (rewrite " + m.getFileName() + "/api-surface.txt in the same commit): " + a));
        }
        assertEquals(List.of(), problems, "the public API differs from the committed baselines");
    }
}
