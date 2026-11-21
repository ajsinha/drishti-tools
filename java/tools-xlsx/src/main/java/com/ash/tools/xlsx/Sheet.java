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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * One worksheet written row by row: rows in order, cells left to right within a row, straight into the workbook's stream, so only
 * the current row is held. What describes the sheet's head (frozen panes, column widths, tab colour, outline, fitting to the page)
 * is set before the first row; merges, drop-downs, conditional formats, the print set-up and charts may be added until the next
 * sheet starts. Styles are named ({@link Styles}); text is always text, never a formula, whatever it starts with; every formula is
 * checked ({@link Formulas#check}) and carries its cached result, so a reader that never recalculates shows the numbers.
 */
public final class Sheet {

    private final Workbook book;
    private final String name;
    private final int index;
    private final TreeMap<Integer, Double> widths = new TreeMap<>();
    private final List<String> conditional = new ArrayList<>();
    private final List<String> merges = new ArrayList<>();
    private final List<String> validations = new ArrayList<>();
    private final List<ChartSpec> charts = new ArrayList<>();
    private final StringBuilder row = new StringBuilder(4096);
    private String tab;
    private Boolean summaryBelow;
    private int freezeRows;
    private int freezeCols;
    private PrintSetup print;
    private int repeatFrom;
    private int repeatTo;
    private boolean begun;
    private boolean finished;
    private int rowNo;
    private int lastCol;
    private long cells;

    Sheet(Workbook book, String name, int index) {
        this.book = book;
        this.name = name;
        this.index = index;
    }

    /** The sheet's name as written (valid and unique: it may differ from the name asked for). */
    public String name() {
        return name;
    }

    /** The cells written so far. */
    public long cells() {
        return cells;
    }

    /** The current row's number (0 before the first row). */
    public int rowNumber() {
        return rowNo;
    }

    // ---- the head: before the first row ----------------------------------------------------------------------------------

    /** Rows and columns kept in view while scrolling. */
    public Sheet freeze(int rows, int cols) {
        head("freeze");
        this.freezeRows = rows;
        this.freezeCols = cols;
        return this;
    }

    /** A column's width in characters ({@code col} 0 is A). */
    public Sheet width(int col, double w) {
        head("width");
        widths.put(col, w);
        return this;
    }

    /** Columns {@code from} to {@code to} (inclusive) all {@code w} wide. */
    public Sheet widths(int from, int to, double w) {
        for (int c = from; c <= to; c++) {
            width(c, w);
        }
        return this;
    }

    /** The tab's colour, six hex digits ({@code 1F3A5F}). */
    public Sheet tab(String rgb) {
        head("tab");
        if (rgb == null || !rgb.matches("[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("a tab colour is six hex digits: " + rgb);
        }
        this.tab = rgb.toUpperCase(java.util.Locale.ROOT);
        return this;
    }

    /** Grouped rows ({@link #row(int, int)}) show their summary row below the group ({@code true}) or above it. */
    public Sheet outline(boolean summaryBelow) {
        head("outline");
        this.summaryBelow = summaryBelow;
        return this;
    }

    /** How the sheet prints; before the first row, since fitting to the page is part of the head. */
    public Sheet print(PrintSetup setup) {
        head("print");
        this.print = setup;
        return this;
    }

    // ---- the tail: until the sheet ends ----------------------------------------------------------------------------------

    /** Rows {@code first} to {@code last} (one-based) are printed at the top of every page. */
    public Sheet repeatRows(int first, int last) {
        tail();
        if (first < 1 || last < first) {
            throw new IllegalArgumentException("rows to repeat: " + first + " to " + last);
        }
        this.repeatFrom = first;
        this.repeatTo = last;
        return this;
    }

    /** A merged range ({@code A1:H1}). */
    public Sheet merge(String range) {
        tail();
        merges.add(range);
        return this;
    }

    /** A drop-down list on {@code sqref}: only {@code options} may be typed. */
    public Sheet list(String sqref, List<String> options) {
        tail();
        validations.add("<dataValidation type=\"list\" allowBlank=\"0\" showErrorMessage=\"1\" sqref=\"" + sqref + "\"><formula1>"
                + Cells.esc("\"" + String.join(",", options) + "\"") + "</formula1></dataValidation>");
        return this;
    }

    /** The cells of {@code sqref} show the {@code pass} look when they equal {@code pass} and the {@code fail} look otherwise (text compared). */
    public Sheet checks(String sqref, String pass) {
        tail();
        String q = Cells.esc("\"" + pass.replace("\"", "\"\"") + "\"");
        int p = conditional.size() * 2;
        conditional.add("<conditionalFormatting sqref=\"" + sqref + "\"><cfRule type=\"cellIs\" dxfId=\"" + book.styles().dxf("pass") + "\" priority=\""
                + (p + 1) + "\" operator=\"equal\"><formula>" + q + "</formula></cfRule><cfRule type=\"cellIs\" dxfId=\"" + book.styles().dxf("fail")
                + "\" priority=\"" + (p + 2) + "\" operator=\"notEqual\"><formula>" + q + "</formula></cfRule></conditionalFormatting>");
        return this;
    }

    /** A native chart of this sheet's data. */
    public Sheet chart(ChartSpec chart) {
        tail();
        charts.add(chart);
        return this;
    }

    // ---- rows and cells --------------------------------------------------------------------------------------------------

    /** Starts row {@code r} (one-based, after the previous one); the cells follow, left to right. */
    public Sheet row(int r) {
        return row(r, 0);
    }

    /** As {@link #row(int)}, at outline level {@code level} (0 none, at most 7): Excel's grouping with + and -. */
    public Sheet row(int r, int level) {
        begin();
        endRow();
        if (r <= rowNo) {
            throw new IllegalStateException(name + ": row " + r + " after row " + rowNo);
        }
        rowNo = r;
        lastCol = -1;
        row.append("<row r=\"").append(r).append('"');
        if (level > 0) {
            row.append(" outlineLevel=\"").append(Math.min(7, level)).append('"');
        }
        row.append('>');
        return this;
    }

    /** The row after the current one. */
    public Sheet next() {
        return row(rowNo + 1, 0);
    }

    /** As {@link #next()}, at outline level {@code level}. */
    public Sheet next(int level) {
        return row(rowNo + 1, level);
    }

    /** Inline text (null writes nothing). */
    public Sheet text(int col, String s, String style) {
        if (s == null) {
            return this;
        }
        Cells.cell(open(col), Cells.ref(col, rowNo), book.styles().index(style), s);
        return counted();
    }

    /** A number; NaN or an infinity writes a styled empty cell. */
    public Sheet number(int col, double v, String style) {
        Cells.cell(open(col), Cells.ref(col, rowNo), book.styles().index(style), v);
        return counted();
    }

    /** A whole number, exact beyond a double's 53 bits. */
    public Sheet number(int col, long v, String style) {
        Cells.cell(open(col), Cells.ref(col, rowNo), book.styles().index(style), v);
        return counted();
    }

    /** A date (its serial day); {@code style} null takes the {@code date} style when the styles have one. */
    public Sheet date(int col, LocalDate d, String style) {
        if (d == null) {
            return this;
        }
        String s = style == null && book.styles().has("date") ? "date" : style;
        Cells.cell(open(col), Cells.ref(col, rowNo), book.styles().index(s), d);
        return counted();
    }

    /** A boolean. */
    public Sheet bool(int col, boolean v, String style) {
        Cells.cell(open(col), Cells.ref(col, rowNo), book.styles().index(style), v);
        return counted();
    }

    /**
     * A value by its Java type, in the standard styles' names when the styles have them: a whole number {@code int}, any other number
     * {@code decimal}, a {@code LocalDate} {@code date}, a boolean, anything else text; null writes nothing.
     */
    public Sheet value(int col, Object v) {
        if (v == null) {
            return this;
        }
        String s = v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte || v instanceof java.math.BigInteger ? "int"
                : v instanceof Number ? "decimal" : v instanceof LocalDate ? "date" : null;
        Cells.cell(open(col), Cells.ref(col, rowNo), s != null && book.styles().has(s) ? book.styles().index(s) : 0, v);
        return counted();
    }

    /**
     * A formula (without its leading {@code =}, checked by {@link Formulas#check}) and its cached result: null writes none (a reader must
     * recalculate), NaN or an infinity caches {@code #NUM!}.
     */
    public Sheet formula(int col, String f, Double cached, String style) {
        String x = Cells.esc(Formulas.check(f));
        StringBuilder b = open(col);
        b.append("<c r=\"").append(Cells.ref(col, rowNo)).append('"');
        Cells.style(b, book.styles().index(style));
        if (cached == null) {
            b.append("><f>").append(x).append("</f></c>");
        } else if (cached.isNaN() || cached.isInfinite()) {
            b.append(" t=\"e\"><f>").append(x).append("</f><v>#NUM!</v></c>");
        } else {
            b.append("><f>").append(x).append("</f><v>").append(Cells.num(cached)).append("</v></c>");
        }
        book.formulaWritten();
        return counted();
    }

    /** A formula whose result is text, with its cached text (null: none). */
    public Sheet formulaText(int col, String f, String cached, String style) {
        String x = Cells.esc(Formulas.check(f));
        StringBuilder b = open(col);
        b.append("<c r=\"").append(Cells.ref(col, rowNo)).append('"');
        Cells.style(b, book.styles().index(style));
        b.append(" t=\"str\"><f>").append(x).append("</f>");
        if (cached != null) {
            b.append("<v>").append(Cells.esc(cached)).append("</v>");
        }
        b.append("</c>");
        book.formulaWritten();
        return counted();
    }

    /** A styled empty cell (a band's fill across). */
    public Sheet blank(int col, String style) {
        Cells.empty(open(col), Cells.ref(col, rowNo), book.styles().index(style));
        return this;
    }

    // ---- the stream ------------------------------------------------------------------------------------------------------

    private Sheet counted() {
        cells++;
        return this;
    }

    private StringBuilder open(int col) {
        if (rowNo == 0 || finished) {
            throw new IllegalStateException(name + ": a cell before the first row() or after the sheet ended");
        }
        if (col <= lastCol) {
            throw new IllegalStateException(name + ": column " + Cells.col(col) + " after column " + Cells.col(lastCol) + " in row " + rowNo);
        }
        lastCol = col;
        return row;
    }

    private void head(String what) {
        if (begun || finished) {
            throw new IllegalStateException(name + ": " + what + " is set before the first row");
        }
    }

    private void tail() {
        if (finished) {
            throw new IllegalStateException(name + ": the sheet has ended (the next one started)");
        }
    }

    private void begin() {
        if (begun) {
            return;
        }
        if (finished) {
            throw new IllegalStateException(name + ": the sheet has ended (the next one started)");
        }
        begun = true;
        StringBuilder b = new StringBuilder(Charts.XML).append("<worksheet ").append(Workbook.MAIN).append(" xmlns:r=\"").append(Workbook.REL_NS).append("\">");
        boolean fit = print != null && print.fitToWidth();
        if (tab != null || summaryBelow != null || fit) {
            b.append("<sheetPr>");
            if (tab != null) {
                b.append("<tabColor rgb=\"FF").append(tab).append("\"/>");
            }
            if (summaryBelow != null) {
                b.append("<outlinePr summaryBelow=\"").append(summaryBelow ? 1 : 0).append("\"/>");
            }
            if (fit) {
                b.append("<pageSetUpPr fitToPage=\"1\"/>");
            }
            b.append("</sheetPr>");
        }
        b.append("<sheetViews><sheetView workbookViewId=\"0\"").append(index == 1 ? " tabSelected=\"1\"" : "").append('>');
        if (freezeRows > 0 || freezeCols > 0) {
            b.append("<pane").append(freezeCols > 0 ? " xSplit=\"" + freezeCols + "\"" : "").append(freezeRows > 0 ? " ySplit=\"" + freezeRows + "\"" : "")
                    .append(" topLeftCell=\"").append(Cells.ref(freezeCols, freezeRows + 1)).append("\" activePane=\"")
                    .append(freezeRows > 0 && freezeCols > 0 ? "bottomRight" : freezeRows > 0 ? "bottomLeft" : "topRight").append("\" state=\"frozen\"/>");
        }
        b.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");
        if (!widths.isEmpty()) {
            b.append("<cols>");
            widths.forEach((c, w) -> b.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(Cells.num(w))
                    .append("\" customWidth=\"1\"/>"));
            b.append("</cols>");
        }
        b.append("<sheetData>");
        book.write(b);
    }

    private void endRow() {
        if (row.length() > 0) {
            row.append("</row>");
            book.write(row);
            row.setLength(0);
        }
    }

    /** Ends the sheet's part (the workbook calls it when the next sheet starts or the workbook closes). */
    void finish() {
        begin();
        endRow();
        finished = true;
        StringBuilder b = new StringBuilder("</sheetData>");
        if (!merges.isEmpty()) {
            b.append("<mergeCells count=\"").append(merges.size()).append("\">");
            merges.forEach(m -> b.append("<mergeCell ref=\"").append(m).append("\"/>"));
            b.append("</mergeCells>");
        }
        conditional.forEach(b::append);
        if (!validations.isEmpty()) {
            b.append("<dataValidations count=\"").append(validations.size()).append("\">");
            validations.forEach(b::append);
            b.append("</dataValidations>");
        }
        if (print != null) {
            b.append(print.xml());
        }
        if (!charts.isEmpty()) {
            b.append("<drawing r:id=\"rId1\"/>");
        }
        b.append("</worksheet>");
        book.write(b);
    }

    int index() {
        return index;
    }

    List<ChartSpec> charts() {
        return charts;
    }

    /** The print titles' reference, or null ({@code 'Name'!$1:$1}). */
    String printTitles() {
        return repeatFrom == 0 ? null : Cells.quoteSheet(name) + "!$" + repeatFrom + ":$" + repeatTo;
    }
}
