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

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Each published module's dependencies outside test scope are exactly its line in {@code dependencies.txt}: a module marked
 * {@code jdk-only} has none, any other names each one. A new dependency, or one dropped, changes the list in the same commit.
 */
class DependenciesTest {

    /** The list: module to its allowed compile and runtime dependencies (empty for jdk-only). */
    static Map<String, Set<String>> listed() {
        Map<String, Set<String>> out = new TreeMap<>();
        for (String l : Sources.resource("dependencies.txt")) {
            String[] w = l.split("\\s+");
            Set<String> deps = new TreeSet<>();
            for (int i = 1; i < w.length; i++) {
                if (!w[i].equals("jdk-only")) {
                    deps.add(w[i]);
                }
            }
            out.put(w[0], deps);
        }
        return out;
    }

    /** What each published module declares outside test scope. */
    static Map<String, Set<String>> declared() {
        Map<String, Set<String>> out = new TreeMap<>();
        for (Path m : Sources.modules()) {
            if (m.getFileName().toString().equals("tools-guards")) {
                continue;
            }
            Set<String> deps = new TreeSet<>();
            for (String d : Sources.dependencies(m)) {
                if (!d.endsWith(":test")) {
                    deps.add(d.substring(0, d.lastIndexOf(':')));
                }
            }
            out.put(m.getFileName().toString(), deps);
        }
        return out;
    }

    @Test
    void everyModulesDependenciesAreItsLine() {
        assertEquals(listed(), declared(), "a module's compile or runtime dependencies differ from dependencies.txt (update both in one commit)");
    }
}
