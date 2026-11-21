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
package com.ash.tools.guards.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shared guards fail on a planted case (a file without the header, one over the cap, a public member removed from a baseline). */
class KitPlantedCaseTest {

    @Test
    void headerAndCapNameThePlantedFiles(@TempDir Path repo) throws Exception {
        Path ok = Files.writeString(repo.resolve("Ok.java"), "// Copyright (c) 2026 Ashutosh Sinha\nclass Ok {}\n");
        Path bare = Files.writeString(repo.resolve("Bare.java"), "class Bare {}\n");
        Path longer = repo.resolve("Long.java");
        Files.writeString(longer, "// Copyright (c) 2026 Ashutosh Sinha\n" + "x\n".repeat(GuardFiles.MAX_LINES));
        List<Path> all = List.of(bare, longer, ok);        // (the folder is under target/, which files() leaves out)
        assertEquals(List.of("Bare.java"), HeaderAndLineCap.withoutHeader(repo, all));
        assertEquals(List.of("Long.java"), HeaderAndLineCap.overCap(repo, all));
        assertEquals(true, Files.exists(ok));
    }

    @Test
    void theSurfaceListsPublicMembersOnly() {
        var out = new java.util.TreeSet<String>();
        ApiSurface.describe(java.util.ArrayList.class, out);
        assertEquals(true, out.stream().anyMatch(l -> l.contains("public boolean java.util.ArrayList.add(E)")));
        assertEquals(false, out.stream().anyMatch(l -> l.contains("private")));
    }
}
