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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The shared rules: letters, references, numbers, escaping, sheet names, dates; and the formula policy. */
class CellsAndFormulasTest {

    @Test
    void columnLettersAndReferences() {
        assertThat(List.of(Cells.col(0), Cells.col(25), Cells.col(26), Cells.col(701), Cells.col(702), Cells.col(16383)))
                .containsExactly("A", "Z", "AA", "ZZ", "AAA", "XFD");
        assertThat(Cells.ref(2, 7)).isEqualTo("C7");
        assertThatThrownBy(() -> Cells.col(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numbersEscapingDatesAndSheetNames() {
        assertThat(List.of(Cells.num(5), Cells.num(-2.5), Cells.num(1e20), Cells.num(0.1))).containsExactly("5", "-2.5", "1.0E20", "0.1");
        assertThat(Cells.esc("a<b>&\"c\"\u0002\t")).isEqualTo("a&lt;b&gt;&amp;&quot;c&quot;\t");
        assertThat(Cells.serial(LocalDate.of(1900, 3, 1))).isEqualTo(61);
        assertThat(Cells.serial(LocalDate.of(2026, 11, 21))).isEqualTo(46347);
        assertThat(Cells.quoteSheet("Main")).isEqualTo("Main");
        assertThat(Cells.quoteSheet("It's here")).isEqualTo("'It''s here'");
        Set<String> used = new HashSet<>();
        assertThat(List.of(Cells.uniqueSheetName("Data", used), Cells.uniqueSheetName("data", used), Cells.uniqueSheetName(" ", used),
                Cells.uniqueSheetName(null, used))).containsExactly("Data", "data (2)", "Sheet", "Sheet (2)");
        assertThat(Cells.headerFooterText("R&D")).isEqualTo("R&&D");
        assertThat(Cells.cell("A1", 0, null)).isEmpty();
        assertThat(Cells.cell("A1", 3, Double.POSITIVE_INFINITY)).isEqualTo("<c r=\"A1\" s=\"3\"/>");
        assertThat(Cells.cell("A1", 0, "=1+1")).contains("t=\"inlineStr\"").doesNotContain("<f>");
    }

    @Test
    void formulasOutsideTheCommonSetAreRefusedAndFound() {
        assertThat(Formulas.functions("SUM(A1:A3)+IF(B1>0,'My sheet'!C1,\"MAX(x)\")+_xlfn.NORM.S.INV(0.99)"))
                .containsExactly("SUM", "IF", "NORM.S.INV");
        assertThat(Formulas.problem("SUM(A1)")).isNull();
        assertThat(Formulas.problem("=SUM(A1)")).contains("leading '='");
        assertThat(Formulas.problem(" ")).isEqualTo("an empty formula");
        assertThat(Formulas.problem("XLOOKUP(1,A:A,B:B)")).contains("XLOOKUP");
        assertThatThrownBy(() -> Formulas.check("OFFSET(A1,1,1)")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OFFSET");
        byte[] x = Workbook.bytes(wb -> wb.sheet("S").row(1).formula(0, "SUM(1,2)", 3.0, null));
        assertThat(Formulas.scan(x)).isEmpty();
        java.util.Map<String, String> parts = Oracle.parts(x);
        String sheet = "xl/worksheets/sheet1.xml";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            WorkbookTemplate.of(parts).fill(java.util.Map.of()).setCell(sheet, "A1", 0).write(out);
            java.util.Map<String, String> edited = new java.util.LinkedHashMap<>(parts);
            edited.put(sheet, parts.get(sheet).replace("<f>SUM(1,2)</f>", "<f>INDIRECT(&quot;B1&quot;)</f>"));
            out.reset();
            WorkbookTemplate.of(edited).write(out);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertThat(Formulas.scan(out.toByteArray())).containsExactly("xl/worksheets/sheet1.xml: INDIRECT in INDIRECT(\"B1\")");
    }
}
