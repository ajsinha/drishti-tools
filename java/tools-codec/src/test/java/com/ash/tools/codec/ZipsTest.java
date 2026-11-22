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
package com.ash.tools.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipsTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void sameContentGivesTheSameBytesWhateverTheInsertionOrder() throws Exception {
        Map<String, byte[]> a = new LinkedHashMap<>();
        a.put("b/two.txt", b("two"));
        a.put("a.txt", b("one"));
        Map<String, byte[]> c = new LinkedHashMap<>();
        c.put("a.txt", b("one"));
        c.put("b/two.txt", b("two"));
        assertThat(Zips.toBytes(a)).isEqualTo(Zips.toBytes(c));
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(Zips.toBytes(a)))) {
            ZipEntry first = z.getNextEntry();
            assertThat(first.getName()).isEqualTo("a.txt");
            assertThat(first.getTime()).isEqualTo(Zips.FIXED_TIME_MILLIS);
        }
    }

    @Test
    void roundTripsThroughReadAndStreamingWriter() throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (Zips.Writer w = Zips.writer(o)) {
            w.add("x.txt", b("hello")).add("dir/y.txt", new ByteArrayInputStream(b("world")));
        }
        o.write(1); // the stream stays open after the archive is finished
        Map<String, byte[]> back = Zips.read(new ByteArrayInputStream(o.toByteArray()), 100);
        assertThat(back.keySet()).containsExactly("x.txt", "dir/y.txt");
        assertThat(back.get("dir/y.txt")).isEqualTo(b("world"));
    }

    @Test
    void duplicateAndUnsafeNamesAreRefusedOnWrite() throws Exception {
        try (Zips.Writer w = Zips.writer(new ByteArrayOutputStream())) {
            w.add("a", b("1"));
            assertThatThrownBy(() -> w.add("a", b("2"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> w.add("../a", b("2"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void zipSlipNamesAreRefusedOnReadAndExtract(@TempDir Path dir) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry("../evil.txt"));
            z.write(1);
            z.closeEntry();
        }
        assertThatThrownBy(() -> Zips.read(new ByteArrayInputStream(o.toByteArray()), 10)).isInstanceOf(IllegalArgumentException.class);
        Path out = dir.resolve("out");
        assertThatThrownBy(() -> Zips.extract(new ByteArrayInputStream(o.toByteArray()), out, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("evil.txt")).doesNotExist();
        for (String bad : new String[] {"/abs", "C:/x", "a\\b", "a/../b", "./a", ""}) {
            assertThatThrownBy(() -> Zips.safeName(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void extractWritesFilesAndStopsAtTheCap(@TempDir Path dir) throws Exception {
        byte[] zip = Zips.toBytes(Map.of("a/b.txt", b("12345"), "c.txt", b("xyz")));
        Map<String, Path> files = Zips.extract(new ByteArrayInputStream(zip), dir.resolve("u"), 100);
        assertThat(Files.readString(files.get("a/b.txt"))).isEqualTo("12345");
        assertThat(files).hasSize(2);
        assertThatThrownBy(() -> Zips.extract(new ByteArrayInputStream(zip), dir.resolve("v"), 6)).hasMessageContaining("larger than the limit");
        try (var s = Files.walk(dir.resolve("v"))) {
            assertThat(s.map(p -> p.getFileName().toString())).as("no partial file is left behind").noneMatch(n -> n.endsWith(".part"));
        }
    }
}
