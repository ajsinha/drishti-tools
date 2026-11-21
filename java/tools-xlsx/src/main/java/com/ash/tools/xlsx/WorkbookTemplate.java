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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A workbook made from a finished one: its parts kept as text (a template committed with the program, its look and its formulas
 * made in Excel), a few cells filled, and the parts copied into a zip on the caller's stream. Every formula the template holds stays
 * live. Immutable: each change returns a new template, so one built-in template serves every request.
 *
 * <pre>{@code
 * WorkbookTemplate t = WorkbookTemplate.of(parts).fill(Map.of("PRODUCT", name));
 * t.setCell("xl/worksheets/sheet1.xml", "B4", 300000).write(out);
 * }</pre>
 */
public final class WorkbookTemplate {

    /** A comment in a template part (a copyright notice), dropped from the workbook. */
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->\\s*", Pattern.DOTALL);
    private static final Pattern COLS_END = Pattern.compile("</cols>");
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final Map<String, String> parts;

    private WorkbookTemplate(Map<String, String> parts) {
        this.parts = parts;
    }

    /** A template of these parts (name to XML text, in the zip's order); comments are dropped. */
    public static WorkbookTemplate of(Map<String, String> parts) {
        Map<String, String> m = new LinkedHashMap<>();
        parts.forEach((k, v) -> m.put(k, COMMENT.matcher(v).replaceAll("")));
        if (!m.containsKey("[Content_Types].xml") || !m.containsKey("xl/workbook.xml")) {
            throw new IllegalArgumentException("a workbook template holds at least [Content_Types].xml and xl/workbook.xml, not " + m.keySet());
        }
        return new WorkbookTemplate(m);
    }

    /** {@code {{KEY}}} in {@code text} replaced by each value, XML-escaped; comments dropped. A part of any template, or any text. */
    public static String fill(String text, Map<String, String> values) {
        String s = COMMENT.matcher(text).replaceAll("");
        for (Map.Entry<String, String> e : values.entrySet()) {
            s = s.replace("{{" + e.getKey() + "}}", Cells.esc(e.getValue() == null ? "" : e.getValue()));
        }
        return s;
    }

    /** The parts' names, in the zip's order. */
    public List<String> partNames() {
        return List.copyOf(parts.keySet());
    }

    /** A part as it is now. */
    public String part(String name) {
        return parts.get(name);
    }

    /** Every part with {@code {{KEY}}} filled ({@link #fill(String, Map)}). */
    public WorkbookTemplate fill(Map<String, String> values) {
        Map<String, String> m = new LinkedHashMap<>();
        parts.forEach((k, v) -> m.put(k, fill(v, values)));
        return new WorkbookTemplate(m);
    }

    /** The cell {@code ref} of the sheet part {@code part} with a new value ({@link Cells#cell}), its style kept; the cell must be there. */
    public WorkbookTemplate setCell(String part, String ref, Object value) {
        String sheet = sheet(part);
        Matcher m = Pattern.compile("<c r=\"" + Pattern.quote(ref) + "\"(?: s=\"(\\d+)\")?[^>]*?(?:/>|>.*?</c>)", Pattern.DOTALL).matcher(sheet);
        if (!m.find()) {
            throw new IllegalArgumentException("the template's part " + part + " has no cell " + ref);
        }
        int style = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        return with(part, sheet.substring(0, m.start()) + Cells.cell(ref, style, value) + sheet.substring(m.end()));
    }

    /**
     * A new cell {@code ref} with {@code style} (an index of the template's own styles) appended to the end of its row, which is
     * created at the end of the sheet's data when the template has none.
     */
    public WorkbookTemplate addCell(String part, String ref, int style, Object value) {
        String sheet = sheet(part);
        int row = Integer.parseInt(ref.replaceFirst("^[A-Z]+", ""));
        String cell = Cells.cell(ref, style, value);
        Matcher m = Pattern.compile("<row r=\"" + row + "\"[^>]*?(?:/>|>.*?(</row>))", Pattern.DOTALL).matcher(sheet);
        if (m.find()) {
            if (m.group(1) == null) {                              // an empty row written <row r="n"/>
                String open = sheet.substring(m.start(), m.end() - 2) + ">";
                return with(part, sheet.substring(0, m.start()) + open + cell + "</row>" + sheet.substring(m.end()));
            }
            return with(part, sheet.substring(0, m.start(1)) + cell + sheet.substring(m.start(1)));
        }
        int end = sheet.indexOf("</sheetData>");
        if (end < 0) {
            throw new IllegalArgumentException("the template's part " + part + " has no sheetData");
        }
        return with(part, sheet.substring(0, end) + "<row r=\"" + row + "\">" + cell + "</row>" + sheet.substring(end));
    }

    /** A column's width (zero-based {@code col}) added to the sheet's {@code <cols>}. */
    public WorkbookTemplate width(String part, int col, double width) {
        String sheet = sheet(part);
        String c = "<col min=\"" + (col + 1) + "\" max=\"" + (col + 1) + "\" width=\"" + Cells.num(width) + "\" customWidth=\"1\"/>";
        if (!sheet.contains("</cols>")) {
            throw new IllegalArgumentException("the template's part " + part + " has no <cols>");
        }
        return with(part, COLS_END.matcher(sheet).replaceFirst(Matcher.quoteReplacement(c + "</cols>")));
    }

    private String sheet(String part) {
        String s = parts.get(part);
        if (s == null) {
            throw new IllegalArgumentException("the template has no part " + part + " (it has " + parts.keySet() + ")");
        }
        return s;
    }

    private WorkbookTemplate with(String part, String text) {
        Map<String, String> m = new LinkedHashMap<>(parts);
        m.put(part, text);
        return new WorkbookTemplate(m);
    }

    /** Writes the workbook to {@code out}, which is flushed, never closed (the same parts give the same bytes). */
    public void write(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(out) {
            @Override
            public void close() throws IOException {
                flush();                                              // the caller's stream stays open
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }
        }, StandardCharsets.UTF_8);
        zip.setLevel(Deflater.BEST_SPEED);
        for (Map.Entry<String, String> p : parts.entrySet()) {
            ZipEntry e = new ZipEntry(p.getKey());
            e.setTimeLocal(ENTRY_TIME);
            zip.putNextEntry(e);
            zip.write(p.getValue().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }
}
