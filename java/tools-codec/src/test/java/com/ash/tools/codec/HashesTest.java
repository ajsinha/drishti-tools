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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashesTest {

    private static final String ABC_256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void knownVectorsInHexLowerCase() {
        assertThat(Hashes.sha256Hex("abc")).isEqualTo(ABC_256);
        assertThat(Hashes.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII))).isEqualTo(ABC_256);
        assertThat(Hashes.hex(Hashes.SHA_384, "abc")).startsWith("cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed");
        assertThat(Hashes.hex("SHA-1", "abc")).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    @Test
    void textIsUtf8() throws Exception {
        String t = "Zürich €";
        byte[] want = MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8));
        assertThat(Hashes.sha256Hex(t)).isEqualTo(HexFormat.of().formatHex(want));
        assertThat(Hashes.hex(Hashes.update(Hashes.sha256(), t))).isEqualTo(HexFormat.of().formatHex(want));
    }

    @Test
    void aFileIsReadAsAStreamAndEqualsItsBytes(@TempDir Path dir) throws Exception {
        byte[] big = new byte[(1 << 16) * 3 + 17];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i * 31);
        }
        Path f = Files.write(dir.resolve("big.bin"), big);
        assertThat(Hashes.sha256Hex(f)).isEqualTo(Hashes.sha256Hex(big));
        assertThat(Hashes.hex(Hashes.SHA_384, f)).isEqualTo(Hashes.hex(Hashes.SHA_384, big));
    }

    @Test
    void anUnknownAlgorithmIsRefusedByName() {
        assertThatThrownBy(() -> Hashes.digest("SHA-999")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-999");
    }
}
