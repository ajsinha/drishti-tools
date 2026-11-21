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
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads CSV (RFC 4180) one record at a time: fields separated by the delimiter (default comma), a field in double quotes may hold
 * the delimiter, line breaks and doubled quotes ({@code ""} is one quote); records end at CRLF, LF or CR. A byte-order mark at the
 * start is dropped. A blank line is a record of one empty field ({@link #blank}); a final line break does not start a record.
 * Text after a closing quote is kept as written (lenient, as spreadsheets are); a quote left open runs to the end of the input.
 * The reader does not close the source unless {@link #close} is called. JDK only.
 */
public final class CsvReader implements AutoCloseable {

    private final Reader in;
    private final char delimiter;
    private int peeked = -2;
    private boolean started;

    /** A reader of comma-separated records from {@code in}. */
    public CsvReader(Reader in) {
        this(in, ',');
    }

    /** A reader of records separated by {@code delimiter} (not a quote, CR or LF). */
    public CsvReader(Reader in, char delimiter) {
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n') {
            throw new IllegalArgumentException("a CSV delimiter cannot be a quote or a line break");
        }
        this.in = in;
        this.delimiter = delimiter;
    }

    /** Every record of {@code text}, comma-separated. */
    public static List<List<String>> parse(String text) {
        return parse(text, ',');
    }

    /** Every record of {@code text}, separated by {@code delimiter}. */
    public static List<List<String>> parse(String text, char delimiter) {
        try (CsvReader r = new CsvReader(new StringReader(text), delimiter)) {
            List<List<String>> out = new ArrayList<>();
            for (List<String> row = r.next(); row != null; row = r.next()) {
                out.add(row);
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);                      // a StringReader does not fail
        }
    }

    /** True for the record of a blank line (one empty field). */
    public static boolean blank(List<String> record) {
        return record.size() == 1 && record.get(0).isEmpty();
    }

    /** The next record, or null at the end of the input. */
    public List<String> next() throws IOException {
        int c = read();
        if (!started) {
            started = true;
            if (c == '﻿') {
                c = read();
            }
        }
        if (c == -1) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        while (true) {
            if (quoted) {
                if (c == -1) {
                    fields.add(cell.toString());
                    return fields;
                }
                if (c == '"') {
                    int n = read();
                    if (n == '"') {
                        cell.append('"');
                    } else {
                        quoted = false;
                        c = n;
                        continue;
                    }
                } else {
                    cell.append((char) c);
                }
            } else if (c == -1 || c == '\n' || c == '\r') {
                if (c == '\r') {
                    int n = read();
                    if (n != '\n') {
                        peeked = n;
                    }
                }
                fields.add(cell.toString());
                return fields;
            } else if (c == delimiter) {
                fields.add(cell.toString());
                cell.setLength(0);
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else {
                cell.append((char) c);
            }
            c = read();
        }
    }

    private int read() throws IOException {
        if (peeked != -2) {
            int c = peeked;
            peeked = -2;
            return c;
        }
        return in.read();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
