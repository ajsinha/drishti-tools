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

import java.io.OutputStream;
import org.junit.jupiter.api.Test;

/**
 * A million cells streamed under a small fixed heap: the build runs this class in its own JVM with {@code -Xmx64m} (the surefire
 * execution {@code large-stream}), so a writer that held the workbook would fail it.
 */
class LargeStreamTest {

    @Test
    void aMillionCellsUnderASmallHeap() {
        long[] bytes = {0};
        OutputStream counting = new OutputStream() {
            @Override
            public void write(int b) {
                bytes[0]++;
            }

            @Override
            public void write(byte[] b, int off, int len) {
                bytes[0] += len;
            }
        };
        long cells;
        try (Workbook wb = Workbook.to(counting)) {
            Sheet s = wb.sheet("Big").freeze(1, 0);
            s.row(1);
            for (int c = 0; c < 10; c++) {
                s.text(c, "Column " + c, "header");
            }
            for (int r = 2; r <= 100_001; r++) {
                s.row(r).text(0, "row " + r, null);
                for (int c = 1; c < 9; c++) {
                    s.number(c, r * 1.5 + c, "decimal");
                }
                s.formula(9, "SUM(B" + r + ":I" + r + ")", 8 * r * 1.5 + 36, "decimal");
            }
            cells = wb.cells();
        }
        assertThat(cells).isEqualTo(1_000_010);
        assertThat(bytes[0]).isGreaterThan(1_000_000);
        assertThat(Runtime.getRuntime().maxMemory()).as("run with a small heap").isLessThan(256L * 1024 * 1024);
    }
}
