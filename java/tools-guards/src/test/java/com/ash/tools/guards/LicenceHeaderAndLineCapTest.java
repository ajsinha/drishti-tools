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
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every source carries the copyright header, and no file is longer than the shared cap ({@link Sources#MAX_LINES}). */
class LicenceHeaderAndLineCapTest {

    private static List<Path> sources() {
        return Sources.files(Sources.REPO, ".java", ".py", "pom.xml", ".md", ".code-workspace", ".run.xml");
    }

    @Test
    void everySourceCarriesTheHeader() {
        List<String> missing = sources().stream()
                .filter(p -> Sources.read(p).lines().limit(20).noneMatch(l -> l.contains("Copyright (c) 2026 Ashutosh Sinha")))
                .map(Sources::rel).toList();
        assertEquals(List.of(), missing, "files without the copyright header");
    }

    @Test
    void noFileIsOverTheCap() {
        List<String> over = sources().stream().filter(p -> Sources.read(p).lines().count() > Sources.MAX_LINES).map(Sources::rel).toList();
        assertEquals(List.of(), over, "files over " + Sources.MAX_LINES + " lines");
    }
}
