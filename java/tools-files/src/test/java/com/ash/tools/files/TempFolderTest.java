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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempFolderTest {

    private static final long PID = ProcessHandle.current().pid();

    @Test
    void madeOnFirstUseMarkedAndRemovedByCleanUp(@TempDir Path base) throws Exception {
        TempFolder t = TempFolder.of(base, "lake-", 3, "lake-temp-folder.txt");
        assertThat(t.made()).isFalse();
        assertThat(t.usage()).containsExactly(0, 0);
        Path root = t.root();
        assertThat(root).isEqualTo(base.toAbsolutePath().normalize().resolve("lake-" + PID + "-3"));
        assertThat(Files.readString(root.resolve("lake-temp-folder.txt"))).isEqualTo("pid " + PID + "\n");
        Path f = t.createFile("x-", ".tmp");
        Files.writeString(f, "12345");
        Path d = t.createDir("work-");
        Path spill = t.subDir("spill");
        assertThat(t.subDir("spill")).isEqualTo(spill);
        assertThat(f.getParent()).isEqualTo(root);
        assertThat(d.getParent()).isEqualTo(root);
        assertThat(t.usage()[1]).isEqualTo(2);                     // the marker and x-*.tmp
        assertThat(t.usage()[0]).isEqualTo(5 + ("pid " + PID + "\n").length());
        t.cleanUp();
        assertThat(root).doesNotExist();
        assertThat(t.made()).isFalse();
        assertThat(t.root()).isEqualTo(root).isDirectory();       // made again on the next use
        t.cleanUp();
    }

    @Test
    void oneFolderPerProcessName(@TempDir Path base) {
        TempFolder t = TempFolder.of(base, "drishti-", "drishti-temp-folder.txt");
        assertThat(t.root().getFileName()).hasToString("drishti-" + PID);
        t.cleanUp();
    }

    @Test
    void staleFoldersAreListedNeverRemoved(@TempDir Path base) throws Exception {
        long dead = deadPid();
        Path ours = Files.createDirectories(base.resolve("lake-" + dead + "-1"));
        Files.writeString(ours.resolve("lake-temp-folder.txt"), "pid " + dead + "\n");
        Path unmarked = Files.createDirectories(base.resolve("lake-" + dead + "-2"));
        Path other = Files.createDirectories(base.resolve("other-" + dead));
        Files.writeString(other.resolve("lake-temp-folder.txt"), "x");
        TempFolder t = TempFolder.of(base, "lake-", 1, "lake-temp-folder.txt");
        t.root();                                                  // ours, alive: not stale
        assertThat(t.staleFolders()).containsExactly(ours);
        assertThat(ours).isDirectory();
        assertThat(unmarked).isDirectory();
        t.cleanUp();
    }

    @Test
    void staticHelpersAndDeleteTree(@TempDir Path base) throws Exception {
        Path f = TempFolder.createFile(base, "a-", ".part");
        Path d = TempFolder.createDir(base, "d-");
        Files.writeString(Files.createDirectories(d.resolve("x/y")).resolve("z.txt"), "z");
        assertThat(f.getParent()).isEqualTo(base);
        TempFolder.deleteTree(d);
        assertThat(d).doesNotExist();
        TempFolder.deleteTree(base.resolve("absent"));             // nothing to do, no failure
    }

    @Test
    void aPrefixIsAPlainName(@TempDir Path base) {
        assertThatThrownBy(() -> TempFolder.of(base, "../x", "m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TempFolder.of(base, "", "m")).isInstanceOf(IllegalArgumentException.class);
    }

    private static long deadPid() {
        for (long p = 4_000_000; p > 3_000_000; p -= 7) {
            if (ProcessHandle.of(p).isEmpty()) {
                return p;
            }
        }
        throw new IllegalStateException("no free process id found");
    }
}
