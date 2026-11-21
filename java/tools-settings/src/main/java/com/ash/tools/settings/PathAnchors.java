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
package com.ash.tools.settings;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one rule that makes a path setting absolute, shared by every program's list of named roots (an install folder, a configuration
 * folder, a project folder): a value is absolute as given, or anchored to a named root ({@code ${config}/views}), or, where the program
 * allows it, under one base folder; it is never taken from the process's working folder. The roots are the caller's: their names and
 * the settings that hold them are parameters, so each program keeps its own thin list of roots over this class.
 */
public final class PathAnchors {

    /** A JDBC URL of a database in a local file (SQLite, DuckDB, H2): its file part is a path. */
    private static final Pattern FILE_DB = Pattern.compile("^(jdbc:(?:sqlite|duckdb|h2:file):)(.+)$");
    /** A value that is not a local relative path: a URL or a scheme ({@code s3:}, {@code mem:}, {@code classpath:}, {@code jdbc:}), a word. */
    private static final Pattern NOT_A_PATH = Pattern.compile(
            "^(?:[A-Za-z][A-Za-z0-9+.-]+:.*|none|auto|off|on|true|false|-?[0-9]+(?:\\.[0-9]+)?)$");
    /** An absolute local path: POSIX, a Windows drive, or a UNC share. */
    private static final Pattern ABSOLUTE = Pattern.compile("^(?:/|\\\\|[A-Za-z]:[\\\\/]).*");
    /** A value anchored to a named root: {@code ${name}} alone or followed by a separator and the rest. */
    private static final Pattern ANCHORED = Pattern.compile("^\\$\\{([A-Za-z0-9_.-]+)\\}(?:[/\\\\](.*))?$");

    private PathAnchors() {
    }

    /** Whether a value is an absolute local path (POSIX, a Windows drive, a UNC share), on any operating system: plain string work. */
    public static boolean isAbsolute(String value) {
        return value != null && ABSOLUTE.matcher(value.strip()).matches();
    }

    /**
     * A path value made absolute with no base folder: absolute as given, or {@code ${root}} / {@code ${root}/rest} under the named root.
     * A bare relative path is refused (the explicit-paths rule).
     *
     * @param value the setting's value
     * @param roots the named roots, each absolute
     * @throws IllegalArgumentException for a bare relative path or an unknown root, naming the value and the roots there are
     */
    public static Path anchored(String value, Map<String, Path> roots) {
        return anchored(value, roots, null);
    }

    /**
     * As {@link #anchored(String, Map)}, but a bare relative path is taken under {@code base} (an install folder) when it is not null.
     */
    public static Path anchored(String value, Map<String, Path> roots, Path base) {
        String x = value == null ? "" : value.strip();
        if (x.isEmpty()) {
            throw new IllegalArgumentException("an empty path");
        }
        Matcher m = ANCHORED.matcher(x);
        if (m.matches()) {
            Path root = roots.get(m.group(1));
            if (root == null) {
                throw new IllegalArgumentException("'" + x + "' names the root ${" + m.group(1) + "}, which is not one of " + roots.keySet());
            }
            return m.group(2) == null || m.group(2).isEmpty() ? root.normalize() : root.resolve(m.group(2)).normalize();
        }
        if (isAbsolute(x)) {
            return Path.of(x).normalize();
        }
        if (base == null) {
            throw new IllegalArgumentException("'" + x + "' is a relative path: give an absolute path or anchor it at one of "
                    + roots.keySet().stream().map(k -> "${" + k + "}").toList() + "; a path is never taken from the working folder");
        }
        return base.resolve(x).normalize();
    }

    /**
     * One value of a path setting made absolute, the one rule of every reader: a relative path is taken under {@code home}, never the
     * working folder; a {@code jdbc:sqlite:}/{@code duckdb:}/{@code h2:file:} URL has its file part anchored; any other URL (a key ending
     * {@code url}), a scheme ({@code s3://}, {@code mem://}, {@code file:}), a word, a number, a JSON path ({@code $.x}, {@code @x}), a
     * template ({@code {x}}) or an absolute path is kept as given.
     *
     * @param key         the setting's name (for the URL rule and the message)
     * @param value       one value (one entry of a list)
     * @param home        the install folder, absolute, or null
     * @param homeSetting the setting that names the install folder, for the message ({@code app.home})
     * @param problems    where a relative value with no install folder is reported (the value is then kept)
     * @return the value to use
     */
    public static String anchorValue(String key, String value, Path home, String homeSetting, List<String> problems) {
        String x = value == null ? "" : value.strip();
        String named = homeSetting + " (" + SettingsFile.envName(homeSetting) + ")";
        Matcher db = FILE_DB.matcher(x);
        if (db.matches()) {                                       // jdbc:sqlite:<file>: the file part, unless absolute or in memory
            String file = db.group(2);
            if (file.startsWith(":") || ABSOLUTE.matcher(file).matches() || file.contains("{")) {
                return value;
            }
            if (home == null) {
                problems.add(key + " is '" + x + "', a database file at a relative path, and " + named + " is not set");
                return value;
            }
            return db.group(1) + home.resolve(file).normalize();
        }
        if (key.endsWith("url") || key.endsWith("URL")) {          // a URL is not a path
            return value;
        }
        if (x.isEmpty() || ABSOLUTE.matcher(x).matches() || NOT_A_PATH.matcher(x).matches() || x.contains("{")
                || x.startsWith("$") || x.startsWith("@")) {             // a JSON path, a placeholder left as text
            return value;
        }
        if (home == null) {
            problems.add(key + " is '" + x + "', a relative path, and " + named + " is not set: give an absolute path, or set " + homeSetting);
            return value;
        }
        return home.resolve(x).normalize().toString();
    }
}
