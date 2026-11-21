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

import java.nio.file.Path;
import java.util.List;

/** The licence header and the line cap, as one check every repository's guard calls. */
public final class HeaderAndLineCap {

    private HeaderAndLineCap() {
    }

    /** The files (relative to {@code repo}) whose first 20 lines carry no copyright line. */
    public static List<String> withoutHeader(Path repo, List<Path> files) {
        return files.stream()
                .filter(p -> GuardFiles.read(p).lines().limit(20).noneMatch(l -> l.contains("Copyright (c) 2026 Ashutosh Sinha")))
                .map(p -> GuardFiles.rel(repo, p)).toList();
    }

    /** The files (relative to {@code repo}) longer than {@link GuardFiles#MAX_LINES} lines. */
    public static List<String> overCap(Path repo, List<Path> files) {
        return files.stream().filter(p -> GuardFiles.read(p).lines().count() > GuardFiles.MAX_LINES)
                .map(p -> GuardFiles.rel(repo, p)).toList();
    }
}
