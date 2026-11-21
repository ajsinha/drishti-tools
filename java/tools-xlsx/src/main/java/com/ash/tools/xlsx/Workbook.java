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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * An Excel workbook (.xlsx, ECMA-376 / ISO 29500 SpreadsheetML) written with the JDK's zip and nothing else, as it is made: straight
 * into the given stream, sheet after sheet ({@link #sheet}) and row after row, so a workbook of a million cells is never held whole.
 * The look is a {@link Styles}; the document's properties ({@link #properties}) and the defined names ({@link #name}) are optional.
 * The same input gives the same bytes (every zip entry carries one fixed time).
 *
 * <pre>{@code
 * try (Workbook wb = Workbook.to(out)) {
 *     Sheet s = wb.sheet("Prices").freeze(1, 0).width(0, 20);
 *     s.row(1).text(0, "Name", "header").text(1, "Price", "header");
 *     s.row(2).text(0, "A").number(1, 12.5, "decimal");
 *     s.row(3).text(0, "Total", "bold").formula(1, "SUM(B2:B2)", 12.5, "number-bold");
 * }
 * }</pre>
 */
public final class Workbook implements AutoCloseable {

    static final String MAIN = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";
    static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String REL = REL_NS + "/";
    private static final String PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    /** Every entry's time: a fixed one, so the same input gives the same bytes. */
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final ZipOutputStream zip;
    private final Writer writer;
    private final Styles styles;
    private final List<Sheet> sheets = new ArrayList<>();
    private final Set<String> used = new HashSet<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final List<int[]> chartParts = new ArrayList<>();
    private Sheet open;
    private String core;
    private String app;
    private boolean recalculate;
    private long formulas;
    private int chartCount;
    private boolean closed;

    private Workbook(OutputStream out, Styles styles) {
        this.zip = new ZipOutputStream(new FilterOutputStream(out) {
            @Override
            public void close() throws IOException {
                flush();                                              // the caller's stream stays open: it is the caller's
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }
        }, StandardCharsets.UTF_8);
        this.zip.setLevel(Deflater.BEST_SPEED);
        this.writer = new BufferedWriter(new OutputStreamWriter(new FilterOutputStream(zip) {
            @Override
            public void close() {
                // the zip is closed by the workbook
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                zip.write(b, off, len);
            }
        }, StandardCharsets.UTF_8), 1 << 16);
        this.styles = styles;
    }

    /** A workbook in the standard look written to {@code out} (which is never closed here). */
    public static Workbook to(OutputStream out) {
        return to(out, Styles.standard());
    }

    /** A workbook in the look {@code styles} written to {@code out} (which is never closed here). */
    public static Workbook to(OutputStream out, Styles styles) {
        return new Workbook(out, styles == null ? Styles.standard() : styles);
    }

    /** The bytes of a workbook in the standard look that {@code build} writes. */
    public static byte[] bytes(Consumer<Workbook> build) {
        return bytes(Styles.standard(), build);
    }

    /** The bytes of a workbook in the look {@code styles} that {@code build} writes. */
    public static byte[] bytes(Styles styles, Consumer<Workbook> build) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
        try (Workbook wb = to(out, styles)) {
            build.accept(wb);
        }
        return out.toByteArray();
    }

    /** The look. */
    public Styles styles() {
        return styles;
    }

    /** Asks Excel to recalculate every formula when the file opens ({@code fullCalcOnLoad}); the cached results stay for other readers. */
    public Workbook recalculateOnOpen() {
        this.recalculate = true;
        return this;
    }

    /** The document's properties: the whole {@code docProps/core.xml} and {@code docProps/app.xml} parts (null: none). */
    public Workbook properties(String coreXml, String appXml) {
        this.core = coreXml;
        this.app = appXml;
        return this;
    }

    /** The next sheet (the previous one is finished first); the name is made valid and unique ({@link Cells#uniqueSheetName}). */
    public Sheet sheet(String name) {
        if (closed) {
            throw new IllegalStateException("the workbook is closed");
        }
        finishSheet();
        Sheet s = new Sheet(this, Cells.uniqueSheetName(name, used), sheets.size() + 1);
        sheets.add(s);
        try {
            writer.flush();
            zip.putNextEntry(entry("xl/worksheets/sheet" + s.index() + ".xml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        open = s;
        return s;
    }

    /** A workbook-level defined name: {@code Confidence} is {@code Main!$B$5}; {@code sheet} as the sheet's {@link Sheet#name()}. */
    public void name(String name, String sheet, String ref) {
        if (name == null || !name.matches("[A-Za-z_\\\\][A-Za-z0-9_.]*") || name.matches("(?i)[A-Z]{1,3}[0-9]+|R[0-9]*C[0-9]*|TRUE|FALSE")) {
            throw new IllegalArgumentException("not a valid defined name: " + name);
        }
        names.put(name, Cells.quoteSheet(sheet) + "!" + ref);
    }

    /** The cells written so far, every sheet. */
    public long cells() {
        return sheets.stream().mapToLong(Sheet::cells).sum();
    }

    /** The formulas written so far, every sheet. */
    public long formulas() {
        return formulas;
    }

    void formulaWritten() {
        formulas++;
    }

    void write(CharSequence s) {
        try {
            writer.append(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ZipEntry entry(String name) {
        ZipEntry e = new ZipEntry(name);
        e.setTimeLocal(ENTRY_TIME);
        return e;
    }

    private void finishSheet() {
        if (open == null) {
            return;
        }
        Sheet s = open;
        open = null;
        s.finish();
        try {
            writer.flush();
            zip.closeEntry();
            if (!s.charts().isEmpty()) {                          // sheet -> drawing -> charts
                int n = s.index();
                part("xl/worksheets/_rels/sheet" + n + ".xml.rels", Charts.XML + "<Relationships xmlns=\"" + PKG_REL + "\"><Relationship Id=\"rId1\" Type=\""
                        + REL + "drawing\" Target=\"../drawings/drawing" + n + ".xml\"/></Relationships>");
                part("xl/drawings/drawing" + n + ".xml", Charts.drawing(s.charts()));
                StringBuilder rels = new StringBuilder(Charts.XML).append("<Relationships xmlns=\"").append(PKG_REL).append("\">");
                for (int i = 0; i < s.charts().size(); i++) {
                    int c = ++chartCount;
                    rels.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"").append(REL).append("chart\" Target=\"../charts/chart").append(c)
                            .append(".xml\"/>");
                    part("xl/charts/chart" + c + ".xml", Charts.chart(s.charts().get(i), s.name()));
                }
                part("xl/drawings/_rels/drawing" + n + ".xml.rels", rels.append("</Relationships>").toString());
                chartParts.add(new int[] {n, s.charts().size()});
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Finishes the last sheet and writes the package parts; the stream given is flushed, never closed. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (sheets.isEmpty()) {
            sheet("Sheet");                                       // a workbook holds at least one sheet
        }
        closed = true;
        finishSheet();
        try {
            part("[Content_Types].xml", contentTypes());
            part("_rels/.rels", Charts.XML + "<Relationships xmlns=\"" + PKG_REL + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + REL + "officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + (core == null ? "" : "<Relationship Id=\"rId2\" Type=\"" + PKG_REL + "/metadata/core-properties\" Target=\"docProps/core.xml\"/>")
                    + (app == null ? "" : "<Relationship Id=\"rId3\" Type=\"" + REL + "extended-properties\" Target=\"docProps/app.xml\"/>")
                    + "</Relationships>");
            part("xl/workbook.xml", workbook());
            part("xl/_rels/workbook.xml.rels", workbookRels());
            part("xl/styles.xml", styles.xml());
            if (core != null) {
                part("docProps/core.xml", core);
            }
            if (app != null) {
                part("docProps/app.xml", app);
            }
            writer.flush();
            zip.finish();
            zip.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void part(String name, String body) throws IOException {
        writer.flush();
        zip.putNextEntry(entry(name));
        writer.write(body);
        writer.flush();
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder b = new StringBuilder(Charts.XML).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        if (core != null) {
            b.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
        }
        if (app != null) {
            b.append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
        }
        for (Sheet s : sheets) {
            b.append("<Override PartName=\"/xl/worksheets/sheet").append(s.index())
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        for (int[] d : chartParts) {
            b.append("<Override PartName=\"/xl/drawings/drawing").append(d[0]).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        }
        for (int c = 1; c <= chartCount; c++) {
            b.append("<Override PartName=\"/xl/charts/chart").append(c).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>");
        }
        return b.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder b = new StringBuilder(Charts.XML).append("<workbook ").append(MAIN).append(" xmlns:r=\"").append(REL_NS)
                .append("\"><bookViews><workbookView activeTab=\"0\"/></bookViews><sheets>");
        for (Sheet s : sheets) {
            b.append("<sheet name=\"").append(Cells.esc(s.name())).append("\" sheetId=\"").append(s.index()).append("\" r:id=\"rId").append(s.index()).append("\"/>");
        }
        b.append("</sheets>");
        StringBuilder defined = new StringBuilder();
        for (Sheet s : sheets) {
            if (s.printTitles() != null) {
                defined.append("<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"").append(s.index() - 1).append("\">").append(Cells.esc(s.printTitles()))
                        .append("</definedName>");
            }
        }
        names.forEach((n, ref) -> defined.append("<definedName name=\"").append(n).append("\">").append(Cells.esc(ref)).append("</definedName>"));
        if (defined.length() > 0) {
            b.append("<definedNames>").append(defined).append("</definedNames>");
        }
        if (recalculate) {
            b.append("<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/>");
        }
        return b.append("</workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder b = new StringBuilder(Charts.XML).append("<Relationships xmlns=\"").append(PKG_REL).append("\">");
        for (Sheet s : sheets) {
            b.append("<Relationship Id=\"rId").append(s.index()).append("\" Type=\"").append(REL).append("worksheet\" Target=\"worksheets/sheet").append(s.index())
                    .append(".xml\"/>");
        }
        return b.append("<Relationship Id=\"rId").append(sheets.size() + 1).append("\" Type=\"").append(REL).append("styles\" Target=\"styles.xml\"/></Relationships>")
                .toString();
    }
}
