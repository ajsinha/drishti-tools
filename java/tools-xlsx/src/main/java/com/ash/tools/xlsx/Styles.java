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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A workbook's look: the {@code xl/styles.xml} part and the names its cell formats ({@code cellXfs}) and differential formats
 * ({@code dxfs}, the conditional formats' looks) are known by. A sheet names a style ({@code "header"}), never an index, so a house
 * style can replace the look without touching the code that writes the cells.
 *
 * <p>{@link #standard()} is the one built-in look (the names below); {@link #of} takes a program's own {@code styles.xml} and names.
 * The standard names: {@code normal}, {@code header} (bold on a light band), {@code int} ({@code #,##0}), {@code decimal}
 * ({@code #,##0.00##}), {@code date} ({@code yyyy-mm-dd}), {@code title} (bold, large), {@code note} (small, grey), {@code bold},
 * {@code number} ({@code #,##0.00}), {@code number-bold}, {@code percent} ({@code 0.00%}); differential formats {@code pass}
 * (green) and {@code fail} (red).
 */
public final class Styles {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String MAIN = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";

    private static final Styles STANDARD = new Styles(XML + "<styleSheet " + MAIN + ">"
            + "<numFmts count=\"4\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00##\"/><numFmt numFmtId=\"165\" formatCode=\"yyyy\\-mm\\-dd\"/>"
            + "<numFmt numFmtId=\"166\" formatCode=\"#,##0.00\"/><numFmt numFmtId=\"167\" formatCode=\"0.00%\"/></numFmts>"
            + "<fonts count=\"4\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font><font><sz val=\"10\"/><color rgb=\"FF6B7686\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEEF1F5\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"11\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"166\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "<dxfs count=\"2\">"
            + "<dxf><font><color rgb=\"FF006100\"/></font><fill><patternFill><bgColor rgb=\"FFC6EFCE\"/></patternFill></fill></dxf>"
            + "<dxf><font><color rgb=\"FF9C0006\"/></font><fill><patternFill><bgColor rgb=\"FFFFC7CE\"/></patternFill></fill></dxf>"
            + "</dxfs></styleSheet>",
            names("normal", "header", "int", "decimal", "date", "title", "note", "bold", "number", "number-bold", "percent"),
            names("pass", "fail"));

    private final String xml;
    private final Map<String, Integer> cellFormats;
    private final Map<String, Integer> differentialFormats;

    private Styles(String xml, Map<String, Integer> cellFormats, Map<String, Integer> differentialFormats) {
        this.xml = xml;
        this.cellFormats = Map.copyOf(cellFormats);
        this.differentialFormats = Map.copyOf(differentialFormats);
    }

    private static Map<String, Integer> names(String... names) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            m.put(names[i], i);
        }
        return m;
    }

    /** The built-in look (the names in the class comment). */
    public static Styles standard() {
        return STANDARD;
    }

    /**
     * A program's own look.
     *
     * @param stylesXml the whole {@code xl/styles.xml} part
     * @param cellFormats each name's index in its {@code cellXfs}
     * @param differentialFormats each name's index in its {@code dxfs} (empty when it has none)
     */
    public static Styles of(String stylesXml, Map<String, Integer> cellFormats, Map<String, Integer> differentialFormats) {
        if (stylesXml == null || !stylesXml.contains("<styleSheet")) {
            throw new IllegalArgumentException("a styles part is an xl/styles.xml styleSheet");
        }
        return new Styles(stylesXml, cellFormats, differentialFormats == null ? Map.of() : differentialFormats);
    }

    /** The {@code xl/styles.xml} part. */
    public String xml() {
        return xml;
    }

    /** Whether a cell format is named {@code name}. */
    public boolean has(String name) {
        return name != null && cellFormats.containsKey(name);
    }

    /** The names of the cell formats. */
    public Set<String> names() {
        return cellFormats.keySet();
    }

    /** A cell format's index by name; 0 (the default format) for null or empty; an unknown name is refused, naming it. */
    public int index(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        Integer i = cellFormats.get(name);
        if (i == null) {
            throw new IllegalArgumentException("no cell style '" + name + "' (the styles name " + new java.util.TreeSet<>(cellFormats.keySet()) + ")");
        }
        return i;
    }

    /** A differential format's index by name ({@code pass}, {@code fail}); an unknown name is refused, naming it. */
    public int dxf(String name) {
        Integer i = differentialFormats.get(name);
        if (i == null) {
            throw new IllegalArgumentException("no differential style '" + name + "' (the styles name "
                    + new java.util.TreeSet<>(differentialFormats.keySet()) + ")");
        }
        return i;
    }
}
