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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvmTempTest {

    @Test
    void theFlagIsFoundOnTheCommandLineOrInTheEnvironment() {
        assertThat(JvmTemp.passed(List.of("-Xmx1g", "-Djava.io.tmpdir=/x"), Map.of())).isTrue();
        assertThat(JvmTemp.passed(List.of(), Map.of("JAVA_TOOL_OPTIONS", "-Djava.io.tmpdir=/x"))).isTrue();
        assertThat(JvmTemp.passed(List.of("-Xmx1g"), Map.of("JDK_JAVA_OPTIONS", "-Xmx1g"))).isFalse();
    }

    @Test
    void theWarningNamesTheBaseAndIsNullWhenTheFlagWasPassed(@TempDir Path base) {
        assertThat(JvmTemp.warning(true, "/tmp", base)).isNull();
        assertThat(JvmTemp.warning(false, "/tmp", base)).contains("-Djava.io.tmpdir=" + base.toAbsolutePath()).contains("/tmp");
    }

    @Test
    void sweepRemovesOnlyOldMatchingFilesAtTheTopLevel(@TempDir Path base) throws Exception {
        Instant now = Instant.now();
        Path old = Files.writeString(base.resolve("libfoo-1.so"), "x");
        Path young = Files.writeString(base.resolve("libbar-2.so"), "x");
        Path other = Files.writeString(base.resolve("notes.txt"), "x");
        Path nested = Files.createDirectories(base.resolve("sub")).resolve("libbaz.so");
        Files.writeString(nested, "x");
        Files.setLastModifiedTime(old, FileTime.from(now.minus(Duration.ofHours(1))));
        Files.setLastModifiedTime(other, FileTime.from(now.minus(Duration.ofHours(1))));
        Files.setLastModifiedTime(nested, FileTime.from(now.minus(Duration.ofHours(1))));
        assertThat(JvmTemp.sweep(base, JvmTemp.NATIVE_COPY, JvmTemp.MIN_AGE, now)).isEqualTo(1);
        assertThat(old).doesNotExist();
        assertThat(young).exists();
        assertThat(other).exists();
        assertThat(nested).exists();
        assertThat(JvmTemp.sweep(base.resolve("absent"), Pattern.compile(".*"), Duration.ZERO, now)).isZero();
    }

    @Test
    void aNativeCopyIsWrittenWholeAndRemovedWithTheFolder(@TempDir Path base) throws Exception {
        TempFolder t = TempFolder.of(base, "jt-", "jt.txt");
        Path copy = JvmTemp.copyNative(t, "libx.so", new ByteArrayInputStream(new byte[] {1, 2, 3}));
        assertThat(Files.readAllBytes(copy)).containsExactly(1, 2, 3);
        assertThat(copy.getParent()).isEqualTo(t.root());
        t.cleanUp();
        assertThat(copy).doesNotExist();
    }

    @Test
    void aFailedCopyLeavesNothingAndABadNameIsRefused(@TempDir Path base) throws Exception {
        TempFolder t = TempFolder.of(base, "jt-", 2, "jt.txt");
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        assertThatThrownBy(() -> JvmTemp.copyNative(t, "liby.so", broken)).isInstanceOf(IOException.class);
        try (var s = Files.list(t.root())) {
            assertThat(s.map(p -> p.getFileName().toString())).containsOnly("jt.txt");
        }
        for (String bad : new String[] {"../x", "a/b", "", ".."}) {
            assertThatThrownBy(() -> JvmTemp.copyNative(t, bad, new ByteArrayInputStream(new byte[0]))).isInstanceOf(IllegalArgumentException.class);
        }
        t.cleanUp();
    }
}
