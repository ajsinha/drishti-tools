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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Every feature of the writer, read back with Apache POI cell by cell: what was written is what a reader sees, every formula POI
 * evaluates equals the cached result the writer stored, and the same input gives the same bytes.
 */
class WorkbookOracleTest {

    /** One expected cell: its sheet, reference, type and value as POI reports it. */
    private record Expect(String sheet, String ref, CellType type, Object value) {}

    private final List<Expect> expected = new ArrayList<>();

    private void expect(String sheet, String ref, CellType type, Object value) {
        expected.add(new Expect(sheet, ref, type, value));
    }

    /** The one workbook every test reads: each feature once. */
    private byte[] everything() {
        expected.clear();
        return Workbook.bytes(wb -> {
            wb.recalculateOnOpen().properties(
                    Charts.XML + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                            + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>All of it</dc:title><dc:creator>tests</dc:creator></cp:coreProperties>",
                    Charts.XML + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>tests</Application></Properties>");
            Sheet s = wb.sheet("Main").freeze(1, 1).width(0, 24).widths(1, 3, 14).tab("1f3a5f")
                    .print(new PrintSetup(true, 9, new double[] {0.5, 0.4, 0.6, 0.4, 0.2, 0.25}, "&C" + Cells.headerFooterText("Q&A"), "&RPage &P of &N", true));
            s.row(1).text(0, "Item", "header").text(1, "Amount", "header").text(2, "When", "header").text(3, "Flag", "header");
            s.row(2).text(0, "=not a formula", null).number(1, 12.5, "decimal").date(2, LocalDate.of(2026, 11, 21), null).bool(3, true, null);
            s.row(3).text(0, "Tab\tand <&> \"quotes\" \u0001gone", "bold").number(1, 9_007_199_254_740_993L, "int").value(2, LocalDate.of(2000, 2, 29))
                    .value(3, false);
            s.row(4).text(0, "NaN is empty", null).number(1, Double.NaN, "number").blank(2, "header").value(3, 42);
            s.row(6).text(0, "Total", "bold").formula(1, "SUM(B2,B3)", 12.5 + 9_007_199_254_740_993d, "number-bold")
                    .formulaText(2, "IF(B2>10,\"big\",\"small\")", "big", null).formula(3, "SQRT(-1)", Double.NaN, null);
            s.row(7).formula(1, "ROUNDDOWN(B2*3,0)", null, null).value(2, 0.25).value(3, new java.math.BigInteger("123456789012345678901"));
            s.merge("A9:D9").list("D2:D4", List.of("TRUE", "FALSE")).checks("C6", "big").repeatRows(1, 1);
            s.row(9).text(0, "merged across", "note");
            wb.name("Total", "Main", "$B$6");
            expect("Main", "A1", CellType.STRING, "Item");
            expect("Main", "A2", CellType.STRING, "=not a formula");
            expect("Main", "B2", CellType.NUMERIC, 12.5);
            expect("Main", "C2", CellType.NUMERIC, LocalDate.of(2026, 11, 21));
            expect("Main", "D2", CellType.BOOLEAN, true);
            expect("Main", "A3", CellType.STRING, "Tab\tand <&> \"quotes\" gone");
            expect("Main", "B3", CellType.NUMERIC, 9_007_199_254_740_993d);
            expect("Main", "C3", CellType.NUMERIC, LocalDate.of(2000, 2, 29));
            expect("Main", "D3", CellType.BOOLEAN, false);
            expect("Main", "B4", CellType.BLANK, null);
            expect("Main", "C4", CellType.BLANK, null);
            expect("Main", "D4", CellType.NUMERIC, 42d);
            expect("Main", "B6", CellType.FORMULA, 12.5 + 9_007_199_254_740_993d);
            expect("Main", "C6", CellType.FORMULA, "big");
            expect("Main", "B7", CellType.FORMULA, null);
            expect("Main", "C7", CellType.NUMERIC, 0.25);
            expect("Main", "D7", CellType.NUMERIC, 123456789012345678901d);
            expect("Main", "A9", CellType.STRING, "merged across");

            Sheet g = wb.sheet("Main").outline(false);                   // a duplicate name is made unique
            g.next().text(0, "Group", "bold").formula(1, "SUM(B2:B3)", 3.0, null);
            g.next(1).text(0, "a", null).number(1, 1, null);
            g.next(1).text(0, "b", null).number(1, 2, null);
            expect("Main (2)", "B1", CellType.FORMULA, 3.0);
            expect("Main (2)", "B3", CellType.NUMERIC, 2d);

            Sheet bad = wb.sheet("a/b:c*d?e[f]g\\h and a name far longer than thirty-one");
            bad.row(1).text(0, bad.name(), null);
            expect(bad.name(), "A1", CellType.STRING, "a b c d e f g h and a name far");
        });
    }

    @Test
    void everyCellReadsBackAsWritten() throws Exception {
        byte[] xlsx = everything();
        try (XSSFWorkbook wb = Oracle.open(xlsx)) {
            for (Expect e : expected) {
                XSSFSheet sheet = wb.getSheet(e.sheet());
                assertThat(sheet).as(e.sheet()).isNotNull();
                org.apache.poi.ss.util.CellReference r = new org.apache.poi.ss.util.CellReference(e.ref());
                Cell c = sheet.getRow(r.getRow()) == null ? null : sheet.getRow(r.getRow()).getCell(r.getCol());
                String at = e.sheet() + "!" + e.ref();
                if (e.type() == CellType.BLANK) {
                    assertThat(c == null || c.getCellType() == CellType.BLANK).as(at).isTrue();
                    continue;
                }
                assertThat(c).as(at).isNotNull();
                assertThat(c.getCellType()).as(at).isEqualTo(e.type());
                switch (e.type()) {
                    case STRING -> assertThat(c.getStringCellValue()).as(at).isEqualTo(e.value());
                    case BOOLEAN -> assertThat(c.getBooleanCellValue()).as(at).isEqualTo(e.value());
                    case NUMERIC -> {
                        if (e.value() instanceof LocalDate d) {
                            assertThat(DateUtil.getLocalDateTime(c.getNumericCellValue()).toLocalDate()).as(at).isEqualTo(d);
                        } else {
                            assertThat(c.getNumericCellValue()).as(at).isEqualTo((Double) e.value());
                        }
                    }
                    case FORMULA -> {
                        if (e.value() instanceof String s) {
                            assertThat(c.getStringCellValue()).as(at).isEqualTo(s);
                        } else if (e.value() != null) {
                            assertThat(c.getNumericCellValue()).as(at).isEqualTo((Double) e.value());
                        }
                    }
                    default -> throw new AssertionError(e);
                }
            }
            assertThat(wb.getSheetAt(0).getRow(5).getCell(1).getCellFormula()).isEqualTo("SUM(B2,B3)");
            assertThat(wb.getSheetAt(0).getRow(5).getCell(3).getCachedFormulaResultType()).isEqualTo(CellType.ERROR);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getCellStyle().getDataFormatString()).isEqualTo("yyyy\\-mm\\-dd");
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFont().getBold()).isTrue();
        }
    }

    @Test
    void poiEvaluatesEveryFormulaToTheCachedResult() throws Exception {
        try (XSSFWorkbook wb = Oracle.open(everything())) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            int checked = 0;
            for (org.apache.poi.ss.usermodel.Sheet s : wb) {
                for (org.apache.poi.ss.usermodel.Row r : s) {
                    for (Cell c : r) {
                        if (c.getCellType() != CellType.FORMULA || c.getCachedFormulaResultType() == CellType.ERROR) {
                            continue;
                        }
                        String at = s.getSheetName() + "!" + c.getAddress();
                        org.apache.poi.ss.usermodel.CellValue v = ev.evaluate(c);
                        if (c.getCachedFormulaResultType() == CellType.STRING) {
                            assertThat(v.getStringValue()).as(at).isEqualTo(c.getStringCellValue());
                        } else if (!c.getCellFormula().startsWith("ROUNDDOWN")) {     // written with no cached result: POI's value is the result
                            assertThat(v.getNumberValue()).as(at).isEqualTo(c.getNumericCellValue());
                        } else {
                            assertThat(v.getNumberValue()).as(at).isEqualTo(37.0);
                        }
                        checked++;
                    }
                }
            }
            assertThat(checked).isEqualTo(4);
        }
    }

    @Test
    void theSheetsLayoutReadsBack() throws Exception {
        try (XSSFWorkbook wb = Oracle.open(everything())) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetName(1)).isEqualTo("Main (2)");
            XSSFSheet s = wb.getSheetAt(0);
            assertThat(s.getPaneInformation().isFreezePane()).isTrue();
            assertThat(s.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(s.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short) 1);
            assertThat(s.getColumnWidth(0)).isEqualTo(24 * 256);
            assertThat(s.getColumnWidth(2)).isEqualTo(14 * 256);
            assertThat(s.getMergedRegions()).containsExactly(CellRangeAddress.valueOf("A9:D9"));
            assertThat(s.getDataValidations()).hasSize(1);
            assertThat(s.getDataValidations().get(0).getValidationConstraint().getExplicitListValues()).containsExactly("TRUE", "FALSE");
            assertThat(s.getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(1);
            assertThat(s.getSheetConditionalFormatting().getConditionalFormattingAt(0).getNumberOfRules()).isEqualTo(2);
            assertThat(s.getPrintSetup().getLandscape()).isTrue();
            assertThat(s.getPrintSetup().getPaperSize()).isEqualTo((short) 9);
            assertThat(s.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(s.getFitToPage()).isTrue();
            assertThat(s.getMargin(org.apache.poi.ss.usermodel.PageMargin.BOTTOM)).isEqualTo(0.6);
            assertThat(s.getHeader().getCenter()).isEqualTo("Q&&A");
            assertThat(s.getFooter().getRight()).isEqualTo("Page &P of &N");
            assertThat(s.getRepeatingRows()).isEqualTo(CellRangeAddress.valueOf("1:1"));
            assertThat(s.getCTWorksheet().getSheetPr().getTabColor().getRgb()).containsExactly(0xFF, 0x1F, 0x3A, 0x5F);
            assertThat(wb.getName("Total").getRefersToFormula()).isEqualTo("Main!$B$6");
            assertThat(wb.getForceFormulaRecalculation()).isTrue();
            assertThat(wb.getProperties().getCoreProperties().getTitle()).isEqualTo("All of it");
            XSSFSheet g = wb.getSheetAt(1);
            assertThat(g.getRow(1).getOutlineLevel()).isEqualTo(1);
            assertThat(g.getRow(0).getOutlineLevel()).isEqualTo(0);
            assertThat(g.getCTWorksheet().getSheetPr().getOutlinePr().getSummaryBelow()).isFalse();
            assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("a b c d e f g h and a name far");
        }
    }

    @Test
    void theSameInputGivesTheSameBytesAndEveryPartIsWellFormed() {
        byte[] a = everything();
        byte[] b = everything();
        assertThat(a).isEqualTo(b);
        Map<String, String> parts = Oracle.parts(a);
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "docProps/core.xml", "docProps/app.xml",
                "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/worksheets/sheet3.xml");
        assertThat(Formulas.scan(a)).isEmpty();
    }

    @Test
    void theCallersStreamStaysOpenAndAnEmptyWorkbookStillHasASheet() throws Exception {
        boolean[] closed = {false};
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        Workbook.to(out).close();
        assertThat(closed[0]).isFalse();
        try (XSSFWorkbook wb = Oracle.open(out.toByteArray())) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
        }
    }

    @Test
    void misuseIsRefusedInWords() {
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.sheet("S").row(2).row(1))).hasMessageContaining("row 1 after row 2");
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.sheet("S").row(1).text(2, "x", null).text(1, "y", null))).hasMessageContaining("column B after column C");
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.sheet("S").row(1).text(0, "x", null).freeze(1, 0)))
                .hasMessageContaining("before the first row");
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.sheet("S").row(1).text(0, "x", "no-such-style"))).hasMessageContaining("no cell style 'no-such-style'");
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.sheet("S").row(1).formula(0, "INDIRECT(\"A1\")", 1.0, null))).hasMessageContaining("INDIRECT");
        assertThatThrownBy(() -> Workbook.bytes(wb -> wb.name("A1", "S", "$A$1"))).hasMessageContaining("not a valid defined name");
        assertThatThrownBy(() -> Workbook.bytes(wb -> {
            Sheet first = wb.sheet("A");
            wb.sheet("B");
            first.merge("A1:B1");
        })).hasMessageContaining("has ended");
    }

    @Test
    void aProgramsOwnStylesAreUsedByName() throws Exception {
        String xml = Styles.standard().xml();
        Styles own = Styles.of(xml, Map.of("plain", 0, "money", 8), Map.of("pass", 0, "fail", 1));
        byte[] x = Workbook.bytes(own, wb -> wb.sheet("S").row(1).number(0, 1234.5, "money"));
        try (XSSFWorkbook wb = Oracle.open(x)) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        }
        assertThatThrownBy(() -> Styles.of("<nope/>", Map.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
