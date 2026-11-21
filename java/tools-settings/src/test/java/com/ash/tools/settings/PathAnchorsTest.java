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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PathAnchorsTest {

    private static final Path HOME = Path.of("/opt/app").toAbsolutePath();
    private static final Map<String, Path> ROOTS = Map.of("config", HOME.resolve("config"), "project", Path.of("/work/p").toAbsolutePath());

    @Test
    void absoluteOnEveryOperatingSystemAsPlainText() {
        assertThat(PathAnchors.isAbsolute("/x/y")).isTrue();
        assertThat(PathAnchors.isAbsolute("C:\\x")).isTrue();
        assertThat(PathAnchors.isAbsolute("d:/x")).isTrue();
        assertThat(PathAnchors.isAbsolute("\\\\server\\share\\x")).isTrue();
        assertThat(PathAnchors.isAbsolute("x/y")).isFalse();
        assertThat(PathAnchors.isAbsolute("C:x")).isFalse();
        assertThat(PathAnchors.isAbsolute(null)).isFalse();
    }

    @Test
    void aNamedRootAnchorsTheRest() {
        assertThat(PathAnchors.anchored("${config}/views/a.yaml", ROOTS)).isEqualTo(HOME.resolve("config/views/a.yaml"));
        assertThat(PathAnchors.anchored("${project}", ROOTS)).isEqualTo(ROOTS.get("project"));
        assertThat(PathAnchors.anchored("${config}/../x", ROOTS)).isEqualTo(HOME.resolve("x"));
    }

    @Test
    void aBareRelativePathIsRefusedWithoutABaseAndUnderItWithOne() {
        assertThatThrownBy(() -> PathAnchors.anchored("views/a.yaml", ROOTS)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative").hasMessageContaining("${config}");
        assertThat(PathAnchors.anchored("views/a.yaml", ROOTS, HOME)).isEqualTo(HOME.resolve("views/a.yaml"));
        assertThatThrownBy(() -> PathAnchors.anchored("${nope}/x", ROOTS)).hasMessageContaining("${nope}");
        assertThatThrownBy(() -> PathAnchors.anchored(" ", ROOTS)).hasMessageContaining("empty");
    }

    @Test
    void anchorValueKeepsWhatIsNotALocalRelativePath() {
        List<String> problems = new ArrayList<>();
        for (String kept : List.of("s3://bucket/x", "mem://a", "none", "true", "42", "1.5", "$.a.b", "@x", "{date}", "/abs/x", "C:\\abs")) {
            assertThat(PathAnchors.anchorValue("app.dir", kept, HOME, "app.home", problems)).as(kept).isEqualTo(kept);
        }
        assertThat(PathAnchors.anchorValue("app.url", "rel/x", HOME, "app.home", problems)).isEqualTo("rel/x");
        assertThat(PathAnchors.anchorValue("app.dir", "data/x", HOME, "app.home", problems)).isEqualTo(HOME.resolve("data/x").toString());
        assertThat(PathAnchors.anchorValue("db", "jdbc:sqlite:db/a.db", HOME, "app.home", problems)).isEqualTo("jdbc:sqlite:" + HOME.resolve("db/a.db"));
        assertThat(PathAnchors.anchorValue("db", "jdbc:sqlite::memory:", HOME, "app.home", problems)).isEqualTo("jdbc:sqlite::memory:");
        assertThat(problems).isEmpty();
    }

    @Test
    void withNoHomeARelativeValueIsReportedNamingTheSettingAndItsVariable() {
        List<String> problems = new ArrayList<>();
        assertThat(PathAnchors.anchorValue("app.dir", "data/x", null, "app.home", problems)).isEqualTo("data/x");
        assertThat(PathAnchors.anchorValue("db", "jdbc:h2:file:db/a", null, "app.home", problems)).isEqualTo("jdbc:h2:file:db/a");
        assertThat(problems).hasSize(2).allMatch(p -> p.contains("app.home (APP_HOME)"));
    }
}
