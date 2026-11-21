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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The test oracle (TL-2): Apache POI opens what the writer made; and the package's parts as text, each well-formed XML. */
final class Oracle {

    private Oracle() {}

    /** The workbook as Apache POI reads it. */
    static XSSFWorkbook open(byte[] xlsx) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The package's parts by name, in the zip's order, each checked to be well-formed XML. */
    static Map<String, String> parts(byte[] xlsx) {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                String text = new String(z.readAllBytes(), StandardCharsets.UTF_8);
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(true);
                f.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                out.put(e.getName(), text);
            }
        } catch (Exception e) {
            throw new AssertionError("not a well-formed package: " + e.getMessage(), e);
        }
        return out;
    }
}
