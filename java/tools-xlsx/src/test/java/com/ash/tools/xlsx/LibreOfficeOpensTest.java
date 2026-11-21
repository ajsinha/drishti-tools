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
package com.ash.tools.xlsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A second reader beside POI, when asked for ({@code -Dtools.test.soffice=true}) and LibreOffice is installed: the workbooks with every
 * feature and with charts are converted to PDF headless. Part of a release's checks with a real Excel; never needed by the build.
 */
class LibreOfficeOpensTest {

    @Test
    void libreOfficeConvertsEveryFeatureAndTheCharts() throws Exception {
        Path soffice = Path.of(System.getProperty("tools.test.soffice.path", "/usr/bin/soffice"));
        Assumptions.assumeTrue(Boolean.getBoolean("tools.test.soffice") && Files.isExecutable(soffice));
        Path dir = Files.createTempDirectory("soffice-check");
        WorkbookOracleTest all = new WorkbookOracleTest();
        java.lang.reflect.Method m = WorkbookOracleTest.class.getDeclaredMethod("everything");
        m.setAccessible(true);
        Files.write(dir.resolve("everything.xlsx"), (byte[]) m.invoke(all));
        Files.write(dir.resolve("charts.xlsx"), Workbook.bytes(wb -> {
            Sheet s = wb.sheet("Data");
            s.row(1).text(0, "Group", "header").text(1, "Amount", "header");
            s.row(2).text(0, "A", null).number(1, 3, null);
            s.row(3).text(0, "B", null).number(1, 5, null);
            s.chart(ChartSpec.of(ChartSpec.Kind.PIE, "Share", 0, List.of(1), 1, 3));
        }));
        for (String name : List.of("everything", "charts")) {
            Process p = new ProcessBuilder(soffice.toString(), "--headless", "-env:UserInstallation=file://" + dir.resolve("profile"), "--convert-to", "pdf",
                    "--outdir", dir.toString(), dir.resolve(name + ".xlsx").toString()).redirectErrorStream(true).start();
            assertThat(p.waitFor(180, TimeUnit.SECONDS)).isTrue();
            assertThat(dir.resolve(name + ".pdf")).exists();
            assertThat(Files.size(dir.resolve(name + ".pdf"))).isGreaterThan(1000);
        }
    }
}
