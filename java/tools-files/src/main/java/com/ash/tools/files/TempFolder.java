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
package com.ash.tools.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * One owner's temporary folder: {@code <base>/<prefix><pid>} (or {@code <prefix><pid>-<n>} when one process holds several), made the
 * first time something needs it, marked with a small text file so a person (or {@link #staleFolders}) can tell it is ours, and
 * removed when the JVM stops cleanly or {@link #cleanUp} is called. The owner chooses the base and the names; nothing here reads a
 * setting or changes {@code java.io.tmpdir}. Left-over folders of dead processes are only listed, never removed: a reused process
 * id or a link inside a folder could lead a delete to files that are not ours. JDK only.
 */
public final class TempFolder {

    private final Path base;
    private final String prefix;
    private final String name;
    private final String marker;
    private volatile Path root;

    private TempFolder(Path base, String prefix, String name, String marker) {
        this.base = base.toAbsolutePath().normalize();
        this.prefix = prefix;
        this.name = name;
        this.marker = marker;
    }

    /**
     * The process's folder {@code <base>/<prefix><pid>}, marked with the file {@code marker}.
     *
     * @param prefix the folder name before the process id ({@code lake-}); letters, digits, dot, dash and underscore
     */
    public static TempFolder of(Path base, String prefix, String marker) {
        return new TempFolder(base, check(prefix), check(prefix) + ProcessHandle.current().pid(), marker);
    }

    /** One of several folders of the process: {@code <base>/<prefix><pid>-<n>}, marked with {@code marker}. */
    public static TempFolder of(Path base, String prefix, int n, String marker) {
        return new TempFolder(base, check(prefix), check(prefix) + ProcessHandle.current().pid() + "-" + n, marker);
    }

    private static String check(String prefix) {
        if (prefix == null || !prefix.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("a temp folder prefix is letters, digits, dot, dash and underscore: " + prefix);
        }
        return prefix;
    }

    /** The base the folder is made under. */
    public Path base() {
        return base;
    }

    /** The folder, made (and its clean-up on a clean stop registered) the first time it is asked for. */
    public Path root() {
        Path r = root;
        if (r != null) {
            return r;
        }
        synchronized (this) {
            if (root == null) {
                Path dir = base.resolve(name);
                try {
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(marker), "pid " + ProcessHandle.current().pid() + "\n");
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot make the temp folder under " + base + ": " + e.getMessage(), e);
                }
                root = dir;
                Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, name + "-cleanup"));
            }
            return root;
        }
    }

    /** True once {@link #root} made the folder (and until {@link #cleanUp}). */
    public boolean made() {
        return root != null;
    }

    /** Removes the folder and everything in it (the shutdown hook; also callable by the owner when it closes). */
    public synchronized void cleanUp() {
        Path r = root;
        if (r != null) {
            deleteTree(r);
            root = null;
        }
    }

    /** A new empty file in the folder. */
    public Path createFile(String filePrefix, String suffix) throws IOException {
        return Files.createTempFile(root(), filePrefix, suffix);
    }

    /** A new folder in the folder. */
    public Path createDir(String dirPrefix) throws IOException {
        return Files.createTempDirectory(root(), dirPrefix);
    }

    /** A named subfolder (made if absent), for a library's spill space. */
    public Path subDir(String sub) {
        try {
            return Files.createDirectories(root().resolve(sub));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The bytes and the files the folder holds now: {@code [bytes, files]} ({@code [0, 0]} before it is made). */
    public long[] usage() {
        long[] size = {0, 0};
        Path r = root;
        if (r == null) {
            return size;
        }
        try (Stream<Path> s = Files.walk(r)) {
            s.filter(Files::isRegularFile).forEach(f -> {
                size[1]++;
                try {
                    size[0] += Files.size(f);
                } catch (IOException e) {
                    // vanished while counted
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // counted as far as it could be
        }
        return size;
    }

    /** The folders under the base, named and marked as this owner's, whose process is gone (to list, for a person to remove). */
    public List<Path> staleFolders() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(base)) {
            for (Path p : s.sorted().toList()) {
                String n = p.getFileName().toString();
                if (!n.startsWith(prefix) || !Files.isRegularFile(p.resolve(marker))) {
                    continue;
                }
                String id = n.substring(prefix.length()).replaceFirst("-\\d{1,9}$", "");
                if (id.matches("\\d{1,10}") && ProcessHandle.of(Long.parseLong(id)).isEmpty()) {
                    out.add(p);
                }
            }
        } catch (IOException e) {
            // a base that cannot be listed holds nothing of ours
        }
        return out;
    }

    /** A new empty file in {@code dir}, for a write that is then moved over its target (same folder, so the move is atomic). */
    public static Path createFile(Path dir, String filePrefix, String suffix) throws IOException {
        return Files.createTempFile(dir, filePrefix, suffix);
    }

    /** A new folder in {@code parent}. */
    public static Path createDir(Path parent, String dirPrefix) throws IOException {
        return Files.createTempDirectory(parent, dirPrefix);
    }

    /** Removes {@code p} and everything under it, as far as it can (a file still open elsewhere stays). */
    public static void deleteTree(Path p) {
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    // a file still open elsewhere
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // already gone
        }
    }
}
