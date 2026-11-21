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

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** A workbook made from a finished one: cells filled, formulas kept live, read back with POI. */
class WorkbookTemplateTest {

    private static final String INPUTS = "xl/worksheets/sheet1.xml";

    /** A template: a workbook this module writes, with a placeholder in a cell and a comment in a part. */
    private static WorkbookTemplate template() {
        byte[] x = Workbook.bytes(wb -> {
            Sheet s = wb.sheet("Inputs").width(0, 30).width(1, 12);
            s.row(1).text(0, "Input", "header").text(1, "Value", "header");
            s.row(2).text(0, "{{PRODUCT}} users", null).number(1, 10, "int");
            s.row(3).text(0, "Hours", null).number(1, 8, "int");
            s.row(4).text(0, "Total", "bold").formula(1, "B2*B3", 80.0, "int");
            s.row(5);
        });
        Map<String, String> parts = new LinkedHashMap<>(Oracle.parts(x));
        parts.put(INPUTS, parts.get(INPUTS).replace("<sheetData>", "<!-- a template's notice --><sheetData>"));
        return WorkbookTemplate.of(parts);
    }

    private static byte[] write(WorkbookTemplate t) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        t.write(out);
        return out.toByteArray();
    }

    @Test
    void cellsAreFilledTheirStylesKeptAndTheFormulasStayLive() throws Exception {
        WorkbookTemplate base = template();
        assertThat(base.part(INPUTS)).doesNotContain("<!--");
        WorkbookTemplate t = base.fill(Map.of("PRODUCT", "R&D")).setCell(INPUTS, "B2", 25).addCell(INPUTS, "C1", 1, "Plan")
                .addCell(INPUTS, "C2", 0, 30.5).addCell(INPUTS, "A5", 0, "an empty row filled").addCell(INPUTS, "A8", 0, "a new row").width(INPUTS, 2, 30);
        try (XSSFWorkbook wb = Oracle.open(write(t))) {
            XSSFSheet s = wb.getSheetAt(0);
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("R&D users");
            assertThat(s.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(25.0);
            assertThat(s.getRow(1).getCell(1).getCellStyle().getDataFormat()).isEqualTo((short) 3);
            assertThat(s.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Plan");
            assertThat(s.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(30.5);
            assertThat(s.getRow(4).getCell(0).getStringCellValue()).isEqualTo("an empty row filled");
            assertThat(s.getRow(7).getCell(0).getStringCellValue()).isEqualTo("a new row");
            assertThat(s.getColumnWidth(2)).isEqualTo(30 * 256);
            assertThat(s.getRow(3).getCell(1).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(wb.getCreationHelper().createFormulaEvaluator().evaluate(s.getRow(3).getCell(1)).getNumberValue()).isEqualTo(200.0);
        }
        assertThat(base.part(INPUTS)).contains("{{PRODUCT}}").as("the template itself is unchanged");
        assertThat(write(t)).isEqualTo(write(t));
    }

    @Test
    void whatIsNotThereIsRefusedInWords() {
        WorkbookTemplate t = template();
        assertThatThrownBy(() -> t.setCell(INPUTS, "Z99", 1)).hasMessageContaining("no cell Z99");
        assertThatThrownBy(() -> t.setCell("xl/worksheets/sheet9.xml", "A1", 1)).hasMessageContaining("no part");
        assertThatThrownBy(() -> WorkbookTemplate.of(Map.of("a.xml", "<a/>"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(WorkbookTemplate.fill("<!-- c -->x {{A}} {{B}}", Map.of("A", "<1>"))).isEqualTo("x &lt;1&gt; {{B}}");
    }
}
