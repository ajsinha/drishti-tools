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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The JVM's own temp folder and the native libraries that unpack themselves into it (JDK only, no product names).
 *
 * <p>Since JDK 17 {@code Files.createTempFile} reads {@code java.io.tmpdir} once, at JVM start, so setting the property later does not
 * move it: a library that unpacks its native code with those calls lands in the system temp folder unless
 * {@code -Djava.io.tmpdir=} is on the command line, and a JVM killed without a clean exit leaves that copy behind. This class tells
 * whether the flag was passed, makes the folder the flag names, sweeps stale native copies from a folder the owner names (never from
 * anywhere else), and copies a native library out of a stream so that the copy is removed at exit.
 */
public final class JvmTemp {

    /** {@code java.io.tmpdir} when this class was first loaded: load it before the property is changed to read the start-up value. */
    private static final String STARTUP = System.getProperty("java.io.tmpdir", "");

    /** Names a library gives its unpacked copy: the library, then a native extension (or a lock file for it). */
    public static final Pattern NATIVE_COPY = Pattern.compile(".*\\.(so|dll|dylib|jnilib)(\\.lck)?", Pattern.CASE_INSENSITIVE);

    /** A copy younger than this may still be on its way to being loaded by another process starting now. */
    public static final Duration MIN_AGE = Duration.ofMinutes(10);

    private JvmTemp() {}

    /** {@code java.io.tmpdir} as the JVM started. */
    public static String startup() {
        return STARTUP;
    }

    /**
     * Makes the folder {@code java.io.tmpdir} names when it does not exist yet (a launcher passes the flag and an IDE run configuration
     * cannot make the folder first).
     *
     * @return the folder made, or null when it existed, none was named, or it could not be made
     */
    public static Path ensureExists() {
        String dir = System.getProperty("java.io.tmpdir", "");
        if (dir.isBlank()) {
            return null;
        }
        Path p = Path.of(dir);
        if (Files.isDirectory(p)) {
            return null;
        }
        try {
            return Files.createDirectories(p);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Whether {@code -Djava.io.tmpdir=} reached this JVM: on its command line, or through {@code JAVA_TOOL_OPTIONS}, {@code JDK_JAVA_OPTIONS} or {@code _JAVA_OPTIONS}. */
    public static boolean passed() {
        List<String> args;
        try {
            args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        } catch (RuntimeException | LinkageError e) {
            args = List.of();
        }
        return passed(args, System.getenv());
    }

    /** {@link #passed()} for the given JVM arguments and environment. */
    public static boolean passed(List<String> inputArgs, Map<String, String> env) {
        if (inputArgs.stream().anyMatch(a -> a.startsWith("-Djava.io.tmpdir="))) {
            return true;
        }
        for (String v : new String[] {env.get("JAVA_TOOL_OPTIONS"), env.get("JDK_JAVA_OPTIONS"), env.get("_JAVA_OPTIONS")}) {
            if (v != null && v.contains("-Djava.io.tmpdir=")) {
                return true;
            }
        }
        return false;
    }

    /** The warning for a log when the flag was not passed (null when it was): where copies go and how to pass it. */
    public static String warning(boolean passed, String startup, Path base) {
        if (passed) {
            return null;
        }
        String b = base.toAbsolutePath().toString();
        return "java.io.tmpdir was not set when Java started, so it is the system temp folder (" + startup
                + "): libraries that unpack native code use it, and a process killed without a clean stop leaves the copies behind."
                + " Start Java with -Djava.io.tmpdir=" + b + ", or set JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=" + b + ".";
    }

    /** {@link #warning(boolean, String, Path)} for this JVM. */
    public static String warning(Path base) {
        return warning(passed(), STARTUP, base);
    }

    /**
     * Removes the native-library copies left in {@code base}: regular files at its top level (no folder, no link) whose names match
     * {@code names} and that are older than {@code minAge}. Nothing outside {@code base}. A copy another process still holds is
     * harmless to unlink on Linux and macOS; on Windows the delete fails and the file is skipped quietly.
     *
     * @return the number removed
     */
    public static int sweep(Path base, Pattern names, Duration minAge, Instant now) {
        int n = 0;
        try (Stream<Path> s = Files.list(base)) {
            for (Path p : s.toList()) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!names.matcher(name).matches() || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(now.minus(minAge))) {
                        continue;
                    }
                    if (Files.deleteIfExists(p)) {
                        n++;
                    }
                } catch (IOException e) {
                    // held open by a live process (Windows), or gone already
                }
            }
        } catch (IOException e) {
            // no base yet: nothing to remove
        }
        return n;
    }

    /** {@link #sweep(Path, Pattern, Duration, Instant)} with {@link #NATIVE_COPY}, {@link #MIN_AGE} and now. */
    public static int sweep(Path base) {
        return sweep(base, NATIVE_COPY, MIN_AGE, Instant.now());
    }

    /**
     * Copies a native library (or any resource a loader needs as a real file) from {@code in} into {@code folder} under {@code fileName}
     * (a plain file name) and returns the copy. The copy is removed when the JVM exits ({@code deleteOnExit}) and with the folder's
     * {@link TempFolder#cleanUp}; a half-written copy is never left (the file appears by an atomic move, and a failed copy is deleted).
     * {@code in} is read to its end and closed.
     */
    public static Path copyNative(TempFolder folder, String fileName, InputStream in) throws IOException {
        if (fileName == null || fileName.isEmpty() || !fileName.equals(Path.of(fileName).getFileName().toString())
                || fileName.equals("..") || fileName.equals(".")) {
            throw new IllegalArgumentException("not a plain file name: " + fileName);
        }
        Path dir = folder.root();
        Path tmp = Files.createTempFile(dir, ".copy-", ".part");
        try (InputStream src = in) {
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
            Path target = dir.resolve(fileName);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            target.toFile().deleteOnExit();
            return target;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
