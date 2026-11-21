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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.ash.tools.guards.kit.GuardFiles;

/** The repository's files as the guards read them: the modules, their sources, their POMs, and the guards' own lists. */
final class Sources {

    /** The repository's root: the build names it outright ({@code tools.repo.root}), never the folder the JVM started in. */
    static final Path REPO = Path.of(System.getProperty("tools.repo.root", "../..")).toAbsolutePath().normalize();
    static final Path JAVA = REPO.resolve("java");
    /** The owner's file cap, one constant for every guard. */
    static final int MAX_LINES = GuardFiles.MAX_LINES;

    private Sources() {
    }

    /** The published modules (every {@code tools-*} folder with a POM and sources), the guards and the BOM left out. */
    static List<Path> modules() {
        try (Stream<Path> s = Files.list(JAVA)) {
            return s.filter(p -> p.getFileName().toString().startsWith("tools-") && Files.isDirectory(p.resolve("src/main/java"))).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every file under {@code root} with one of the suffixes, build output and the IDE's and git's folders left out. */
    static List<Path> files(Path root, String... suffixes) {
        return GuardFiles.files(root, suffixes);
    }

    /** Every main source of the published modules. */
    static List<Path> mainSources() {
        List<Path> out = new ArrayList<>();
        for (Path m : modules()) {
            out.addAll(files(m.resolve("src/main"), ".java", ".properties", ".xml", ".txt"));
        }
        return out;
    }

    static String read(Path p) {
        return GuardFiles.read(p);
    }

    static String rel(Path p) {
        return GuardFiles.rel(REPO, p);
    }

    /** The lines of a guard's list (test resources), comments and blank lines left out. */
    static List<String> resource(String name) {
        try (InputStream in = Sources.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new AssertionError("the guards have no list " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A module's direct dependencies, as {@code group:artifact:scope} ({@code compile} when no scope is written). */
    static Set<String> dependencies(Path module) {
        String pom = read(module.resolve("pom.xml")).replaceAll("(?s)<dependencyManagement>.*?</dependencyManagement>", "")
                .replaceAll("(?s)<parent>.*?</parent>", "").replaceAll("(?s)<!--.*?-->", "");
        Set<String> out = new TreeSet<>();
        Matcher d = Pattern.compile("(?s)<dependency>(.*?)</dependency>").matcher(pom);
        while (d.find()) {
            String scope = find(d.group(1), "scope");
            out.add(find(d.group(1), "groupId") + ":" + find(d.group(1), "artifactId") + ":" + (scope == null ? "compile" : scope));
        }
        return out;
    }

    private static String find(String body, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*([^<]+?)\\s*</" + tag + ">").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** The exemptions one guard grants ({@code exemptions.txt}: {@code <guard> <path> <reason>}), as paths. */
    static Set<String> exemptions(String guard) {
        Set<String> out = new TreeSet<>();
        for (String l : resource("exemptions.txt")) {
            String[] w = l.split("\\s+", 3);
            if (w[0].equals(guard)) {
                out.add(w[1]);
            }
        }
        return out;
    }
}
