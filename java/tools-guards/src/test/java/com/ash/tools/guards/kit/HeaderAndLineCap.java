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

    /** The mark every file is expected to carry, unless the repository names its own. */
    public static final String DEFAULT_MARK = "Copyright (c) 2026 Ashutosh Sinha";

    /** How many lines from the top of a file the mark may appear in, unless the repository names its own. */
    public static final int DEFAULT_WINDOW = 20;

    /** The files (relative to {@code repo}) whose first {@link #DEFAULT_WINDOW} lines do not contain {@link #DEFAULT_MARK}. */
    public static List<String> withoutHeader(Path repo, List<Path> files) {
        return withoutHeader(repo, files, DEFAULT_MARK, DEFAULT_WINDOW);
    }

    /** As {@link #withoutHeader(Path, List)} with the repository's own header text, looked for in the first {@link #DEFAULT_WINDOW} lines. */
    public static List<String> withoutHeader(Path repo, List<Path> files, String mark) {
        return withoutHeader(repo, files, mark, DEFAULT_WINDOW);
    }

    /** The files (relative to {@code repo}) whose first {@code window} lines do not contain {@code mark} (a blank mark is refused). */
    public static List<String> withoutHeader(Path repo, List<Path> files, String mark, int window) {
        if (mark == null || mark.isBlank()) {
            throw new IllegalArgumentException("the header mark must not be blank");
        }
        if (window < 1) {
            throw new IllegalArgumentException("the header window must be at least one line: " + window);
        }
        return files.stream()
                .filter(p -> GuardFiles.read(p).lines().limit(window).noneMatch(l -> l.contains(mark)))
                .map(p -> GuardFiles.rel(repo, p)).toList();
    }

    /** The files (relative to {@code repo}) longer than {@link GuardFiles#MAX_LINES} lines. */
    public static List<String> overCap(Path repo, List<Path> files) {
        return files.stream().filter(p -> GuardFiles.read(p).lines().count() > GuardFiles.MAX_LINES)
                .map(p -> GuardFiles.rel(repo, p)).toList();
    }
}
