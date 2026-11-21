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
package com.ash.tools.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

/**
 * Writes CSV (RFC 4180): a field is quoted when it holds the delimiter, a quote, CR or LF (quotes doubled inside); records end with
 * CRLF unless {@link #lineEnd} says otherwise; {@code null} is an empty field, any other value its {@code String.valueOf} (format
 * numbers before, as the export wants them). Optional: a byte-order mark first ({@link #bom}, spreadsheets then read UTF-8), and the
 * formula guard ({@link #formulaGuard}): text a spreadsheet would run as a formula ({@code = + - @}, tab or CR first, and not a
 * number) gets a leading apostrophe so it stays text. The writer never closes or flushes the caller's {@link Appendable}. JDK only.
 */
public final class CsvWriter {

    private final Appendable out;
    private char delimiter = ',';
    private String lineEnd = "\r\n";
    private boolean guard;

    private CsvWriter(Appendable out) {
        this.out = out;
    }

    /** A writer to {@code out} (a {@link StringBuilder}, a {@link java.io.Writer}, ...). */
    public static CsvWriter to(Appendable out) {
        return new CsvWriter(out);
    }

    /** The field delimiter (default comma; not a quote, CR or LF). */
    public CsvWriter delimiter(char d) {
        if (d == '"' || d == '\r' || d == '\n') {
            throw new IllegalArgumentException("a CSV delimiter cannot be a quote or a line break");
        }
        this.delimiter = d;
        return this;
    }

    /** The record end (default CRLF, as RFC 4180). */
    public CsvWriter lineEnd(String end) {
        this.lineEnd = end;
        return this;
    }

    /** Guards text cells that a spreadsheet would run as a formula (a leading apostrophe). */
    public CsvWriter formulaGuard(boolean on) {
        this.guard = on;
        return this;
    }

    /** Writes the UTF-8 byte-order mark (call before the first record). */
    public CsvWriter bom() {
        return append("﻿");
    }

    /** Writes one record. */
    public CsvWriter row(List<?> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                b.append(delimiter);
            }
            Object v = cells.get(i);
            String s = v == null ? "" : String.valueOf(v);
            b.append(quote(guard ? guard(s) : s, delimiter));
        }
        return append(b.append(lineEnd));
    }

    /** Writes one record. */
    public CsvWriter row(Object... cells) {
        return row(Arrays.asList(cells));
    }

    /** {@code cell} as a field: quoted when it holds {@code delimiter}, a quote, CR or LF. */
    public static String quote(String cell, char delimiter) {
        for (int i = 0; i < cell.length(); i++) {
            char c = cell.charAt(i);
            if (c == delimiter || c == '"' || c == '\n' || c == '\r') {
                return '"' + cell.replace("\"", "\"\"") + '"';
            }
        }
        return cell;
    }

    /** {@code text} with a leading apostrophe when a spreadsheet would run it as a formula and it is not a number. */
    public static String guard(String text) {
        if (text.isEmpty() || "=+-@\t\r".indexOf(text.charAt(0)) < 0) {
            return text;
        }
        try {
            Double.parseDouble(text);
            return text;
        } catch (NumberFormatException e) {
            return "'" + text;
        }
    }

    private CsvWriter append(CharSequence s) {
        try {
            out.append(s);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
