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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The modules' rules: one package per module ({@code com.ash.tools.<module>}); a module uses another only where its POM says so
 * (compile or test scope) and the main code only along the compile edges of {@code dependencies.txt}; no cycle; nothing in an
 * {@code internal} package is used from another module.
 */
class ModuleRulesTest {

    private static final Pattern IMPORT = Pattern.compile("(?m)\\bcom\\.ash\\.tools\\.([a-z]+)(\\.internal)?\\b");

    private static String pkg(Path module) {
        return module.getFileName().toString().substring("tools-".length());
    }

    @Test
    void eachModuleHoldsItsOwnPackageOnly() {
        List<String> wrong = new ArrayList<>();
        for (Path m : Sources.modules()) {
            String want = "com.ash.tools." + pkg(m);
            for (Path f : Sources.files(m.resolve("src"), ".java")) {
                Matcher p = Pattern.compile("(?m)^package ([\\w.]+);").matcher(Sources.read(f));
                if (!p.find() || !(p.group(1).equals(want) || p.group(1).startsWith(want + "."))) {
                    wrong.add(Sources.rel(f));
                }
            }
        }
        assertEquals(List.of(), wrong, "a source outside its module's package");
    }

    @Test
    void codeUsesAnotherModuleOnlyAlongADeclaredEdgeAndNeverItsInternals() {
        Map<String, Set<String>> compile = new TreeMap<>();
        DependenciesTest.listed().forEach((k, v) -> {
            Set<String> mods = new TreeSet<>();
            v.stream().filter(d -> d.startsWith("com.ash.tools:tools-")).forEach(d -> mods.add(d.substring("com.ash.tools:tools-".length())));
            compile.put(k.substring("tools-".length()), mods);
        });
        List<String> wrong = new ArrayList<>();
        for (Path m : Sources.modules()) {
            String self = pkg(m);
            Set<String> testEdges = new TreeSet<>();
            Sources.dependencies(m).stream().filter(d -> d.startsWith("com.ash.tools:tools-"))
                    .forEach(d -> testEdges.add(d.substring("com.ash.tools:tools-".length(), d.lastIndexOf(':'))));
            for (String part : List.of("main", "test")) {
                Path dir = m.resolve("src").resolve(part);
                if (!dir.toFile().isDirectory()) {
                    continue;
                }
                for (Path f : Sources.files(dir, ".java")) {
                    Matcher im = IMPORT.matcher(Sources.read(f));
                    while (im.find()) {
                        String used = im.group(1);
                        if (used.equals(self) || self.equals("guards")) {
                            continue;
                        }
                        boolean allowed = part.equals("main") ? compile.getOrDefault(self, Set.of()).contains(used) : testEdges.contains(used);
                        if (!allowed) {
                            wrong.add(Sources.rel(f) + " uses tools-" + used + " (" + part + ")");
                        } else if (im.group(2) != null) {
                            wrong.add(Sources.rel(f) + " uses tools-" + used + "'s internal package");
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), wrong.stream().distinct().toList(), "a module used without a declared edge");
    }

    @Test
    void theModulesFormNoCycle() {
        Map<String, Set<String>> edges = new TreeMap<>();
        for (Path m : Sources.modules()) {
            Set<String> to = new TreeSet<>();
            Sources.dependencies(m).stream().filter(d -> d.startsWith("com.ash.tools:tools-"))
                    .forEach(d -> to.add(d.substring("com.ash.tools:".length(), d.lastIndexOf(':'))));
            edges.put(m.getFileName().toString(), to);
        }
        for (String start : edges.keySet()) {
            Deque<String> todo = new ArrayDeque<>(edges.get(start));
            Set<String> seen = new TreeSet<>();
            while (!todo.isEmpty()) {
                String n = todo.pop();
                assertTrue(!n.equals(start), "a cycle through " + start);
                if (seen.add(n)) {
                    todo.addAll(edges.getOrDefault(n, Set.of()));
                }
            }
        }
    }
}
