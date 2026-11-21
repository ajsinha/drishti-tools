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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The repository's files as every guard reads them (the published test jar: Lake and Drishti use it, so the guards are written once). */
public final class GuardFiles {

    /** The owner's file cap, one constant for every guard in every repository. */
    public static final int MAX_LINES = 2500;

    private GuardFiles() {
    }

    /** Every file under {@code root} with one of the suffixes, build output and the IDE's and git's folders left out. */
    public static List<Path> files(Path root, String... suffixes) {
        try (Stream<Path> w = Files.walk(root)) {
            return w.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/target/") && !s.contains("/.mvn/") && !s.contains("/.git/") && !s.contains("/.idea/");
                    })
                    .filter(p -> Stream.of(suffixes).anyMatch(x -> p.getFileName().toString().endsWith(x)))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code p} relative to {@code repo}, with forward slashes. */
    public static String rel(Path repo, Path p) {
        return repo.relativize(p).toString().replace('\\', '/');
    }
}
