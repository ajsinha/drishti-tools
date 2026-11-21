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

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

/**
 * The small rules every part of a workbook shares, written once: column letters and A1 references, a number as a value cell holds
 * it, XML escaping, a sheet's name made valid and unique and quoted in a reference, a date as Excel's serial day, a header or footer
 * text with Excel's codes neutralised, and one cell's XML (inline text, never a formula, whatever it starts with).
 */
public final class Cells {

    /** Excel's day zero for the 1900 date system as every reader computes it (1899-12-30: the 1900 leap-year bug included). */
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);
    /** The longest sheet name Excel accepts. */
    public static final int MAX_SHEET_NAME = 31;

    private Cells() {}

    /** Column letters: 0 is A, 25 is Z, 26 is AA. */
    public static String col(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("a column index is 0 or more: " + index);
        }
        StringBuilder b = new StringBuilder();
        for (int n = index; n >= 0; n = n / 26 - 1) {
            b.insert(0, (char) ('A' + n % 26));
        }
        return b.toString();
    }

    /** The A1 reference of a zero-based column and a one-based row ({@code ref(2, 7)} is {@code C7}). */
    public static String ref(int col, int row) {
        return col(col) + row;
    }

    /** A number as a value cell holds it: a whole number without a fraction, otherwise the shortest text that reads back to the same double. */
    public static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    /** Text escaped for XML; control characters XML cannot hold are dropped (tab, line feed and carriage return are kept). */
    public static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        b.append(c);                                  // other control characters are not valid XML
                    }
                }
            }
        }
        return b.toString();
    }

    /** A sheet's name as a reference writes it ({@code Main}, {@code 'Rest of scope'}, {@code 'It''s'}). */
    public static String quoteSheet(String sheet) {
        return sheet.matches("[A-Za-z_][A-Za-z0-9_]*") ? sheet : "'" + sheet.replace("'", "''") + "'";
    }

    /**
     * A valid, unique sheet name: at most 31 characters, none of {@code [ ] : * ? / \}, not blank, and not already in {@code used}
     * (compared without case; the name is added to it): {@code Sheet}, {@code Sheet (2)}.
     */
    public static String uniqueSheetName(String wanted, Set<String> used) {
        String base = (wanted == null || wanted.isBlank() ? "Sheet" : wanted).replaceAll("[\\[\\]:*?/\\\\]", " ").strip();
        base = base.isEmpty() ? "Sheet" : base.length() > MAX_SHEET_NAME ? base.substring(0, MAX_SHEET_NAME).strip() : base;
        String name = base;
        for (int i = 2; !used.add(name.toLowerCase(Locale.ROOT)); i++) {
            String suffix = " (" + i + ")";
            name = (base.length() + suffix.length() > MAX_SHEET_NAME ? base.substring(0, MAX_SHEET_NAME - suffix.length()) : base) + suffix;
        }
        return name;
    }

    /** A date as Excel's serial day number (1900 date system). */
    public static long serial(LocalDate d) {
        return ChronoUnit.DAYS.between(EPOCH, d);
    }

    /** A header or footer text with Excel's codes neutralised ({@code &} doubled), to be placed between codes such as {@code &L}. */
    public static String headerFooterText(String s) {
        return s == null ? "" : s.replace("&", "&&");
    }

    /**
     * One cell's XML for a value by its Java type: a whole number ({@code Long}, {@code Integer}, {@code Short}, {@code Byte},
     * {@code BigInteger}) or any other {@code Number} as a number, a {@code Boolean}, a {@code LocalDate} as its serial day, anything
     * else as inline text. A null value, NaN or an infinity writes a styled empty cell, or nothing when {@code style} is 0.
     *
     * @param ref the cell ({@code C7})
     * @param style the cell format's index, 0 for none
     */
    public static String cell(String ref, int style, Object v) {
        StringBuilder b = new StringBuilder(64);
        cell(b, ref, style, v);
        return b.toString();
    }

    static void cell(StringBuilder b, String ref, int style, Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            value(b, ref, style, Long.toString(((Number) v).longValue()), null);
        } else if (v instanceof BigInteger n) {
            value(b, ref, style, n.toString(), null);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                empty(b, ref, style);
            } else {
                value(b, ref, style, num(d), null);
            }
        } else if (v instanceof Boolean x) {
            value(b, ref, style, x ? "1" : "0", "b");
        } else if (v instanceof LocalDate d) {
            value(b, ref, style, Long.toString(serial(d)), null);
        } else if (v == null) {
            empty(b, ref, style);
        } else {
            b.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
            style(b, style);
            b.append("><is><t xml:space=\"preserve\">").append(esc(v.toString())).append("</t></is></c>");
        }
    }

    static void value(StringBuilder b, String ref, int style, String v, String type) {
        b.append("<c r=\"").append(ref).append('"');
        style(b, style);
        if (type != null) {
            b.append(" t=\"").append(type).append('"');
        }
        b.append("><v>").append(v).append("</v></c>");
    }

    static void empty(StringBuilder b, String ref, int style) {
        if (style != 0) {
            b.append("<c r=\"").append(ref).append('"');
            style(b, style);
            b.append("/>");
        }
    }

    static void style(StringBuilder b, int style) {
        if (style != 0) {
            b.append(" s=\"").append(style).append('"');
        }
    }
}
