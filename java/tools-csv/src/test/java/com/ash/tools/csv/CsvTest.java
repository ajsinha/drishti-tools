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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/** RFC 4180 vectors, and the oracle: what the writer writes, Apache Commons CSV reads back the same, and the reader reads what Commons writes. */
class CsvTest {

    @Test
    void rfc4180Vectors() {
        assertThat(CsvReader.parse("a,b,c\r\n1,2,3\r\n")).containsExactly(List.of("a", "b", "c"), List.of("1", "2", "3"));
        assertThat(CsvReader.parse("a,b\n1,2")).containsExactly(List.of("a", "b"), List.of("1", "2"));
        assertThat(CsvReader.parse("\"a,b\",\"say \"\"hi\"\"\",\"line\r\nbreak\"\r\nx,,\r\n"))
                .containsExactly(List.of("a,b", "say \"hi\"", "line\r\nbreak"), List.of("x", "", ""));
        assertThat(CsvReader.parse("a\r\rb\n")).containsExactly(List.of("a"), List.of(""), List.of("b"));
        assertThat(CsvReader.parse("﻿id;name\n1;x", ';')).containsExactly(List.of("id", "name"), List.of("1", "x"));
        assertThat(CsvReader.parse("")).isEmpty();
        assertThat(CsvReader.parse("\"open")).containsExactly(List.of("open"));
        assertThat(CsvReader.blank(CsvReader.parse("\n").get(0))).isTrue();
    }

    @Test
    void writerQuotesOnlyWhatNeedsIt() {
        StringBuilder b = new StringBuilder();
        CsvWriter.to(b).row("a", "b,c", "q\"q", null, 1.5).row(List.of("x\ny"));
        assertThat(b).hasToString("a,\"b,c\",\"q\"\"q\",,1.5\r\n\"x\ny\"\r\n");
        StringBuilder t = new StringBuilder();
        CsvWriter.to(t).delimiter('\t').lineEnd("\n").bom().row("a b", "c\td");
        assertThat(t).hasToString("﻿a b\t\"c\td\"\n");
        assertThatThrownBy(() -> CsvWriter.to(b).delimiter('"')).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formulaGuard() {
        StringBuilder b = new StringBuilder();
        CsvWriter.to(b).formulaGuard(true).row("=SUM(A1)", "-12.5", "+x", "@me", "\tx", "plain", "-");
        assertThat(b).hasToString("'=SUM(A1),-12.5,'+x,'@me,'\tx,plain,'-\r\n");
    }

    @Test
    void roundTripAgainstCommonsCsv() throws Exception {
        Random r = new Random(7);
        for (int n = 0; n < 200; n++) {
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1 + r.nextInt(4); i > 0; i--) {
                List<String> row = new ArrayList<>();
                for (int j = 1 + r.nextInt(4); j > 0; j--) {
                    row.add(cell(r));
                }
                rows.add(row);
            }
            StringBuilder ours = new StringBuilder();
            CsvWriter w = CsvWriter.to(ours);
            rows.forEach(w::row);
            List<List<String>> theirs = new ArrayList<>();
            for (CSVRecord rec : CSVFormat.RFC4180.parse(new StringReader(ours.toString()))) {
                theirs.add(rec.toList());
            }
            assertThat(theirs).isEqualTo(rows);
            StringBuilder commons = new StringBuilder();
            try (var p = new org.apache.commons.csv.CSVPrinter(commons, CSVFormat.RFC4180)) {
                for (List<String> row : rows) {
                    p.printRecord(row);
                }
            }
            assertThat(CsvReader.parse(commons.toString())).isEqualTo(rows);
        }
    }

    private static String cell(Random r) {
        String alphabet = "ab ,\"\r\n;x1";
        StringBuilder b = new StringBuilder("v");                  // a first character, so a record is never one empty field
        for (int i = r.nextInt(6); i > 0; i--) {
            b.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return b.toString();
    }
}
