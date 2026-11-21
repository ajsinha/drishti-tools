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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretsTest {

    @Test
    void aReferenceIsReadThroughTheViewAndAFileTwinWins(@TempDir Path dir) throws Exception {
        Map<String, String> env = Map.of("PW", "s3cret");
        assertThat(Secrets.get(Map.of("password", "${PW}"), "password", env::get)).isEqualTo("s3cret");
        assertThat(Secrets.get(Map.of("password", "  "), "password", env::get)).isNull();
        Path f = dir.resolve("pw.txt");
        Files.writeString(f, "from-file  \nsecond line\n");
        assertThat(Secrets.secret(Map.of("password", "x", "password-file", f.toString()), "password", env::get)).isEqualTo("from-file");
    }

    @Test
    void anUnnamedReferenceOrAMissingFileIsASettingExceptionNamingTheSetting(@TempDir Path dir) {
        assertThatThrownBy(() -> Secrets.get(Map.of("password", "${NOPE_NOT_SET_ANYWHERE}"), "password", k -> null))
                .isInstanceOf(SettingException.class).hasMessageContaining("password").hasMessageContaining("NOPE_NOT_SET_ANYWHERE");
        assertThatThrownBy(() -> Secrets.secret(Map.of("password-file", dir.resolve("none").toString()), "password", k -> null))
                .isInstanceOf(SettingException.class).hasMessageContaining("file not found");
    }
}
