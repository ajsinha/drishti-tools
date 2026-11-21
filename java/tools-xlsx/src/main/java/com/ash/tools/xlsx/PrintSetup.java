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

import java.util.Locale;

/**
 * How a sheet prints: the orientation, Excel's paper size code (1 letter, 9 A4; 0 leaves Excel's own), the margins in inches (top,
 * right, bottom, left, and optionally the header's and the footer's distance: 4 or 6 numbers; null leaves Excel's own), the header and
 * footer in Excel's codes ({@code &L}, {@code &C}, {@code &R}, {@code &P} page, {@code &N} pages, {@code &D} date; literal text through
 * {@link Cells#headerFooterText}), and whether the sheet is fitted to one page's width (as many pages tall as it needs).
 */
public record PrintSetup(boolean landscape, int paperSize, double[] margins, String header, String footer, boolean fitToWidth) {

    public PrintSetup {
        if (margins != null && margins.length != 4 && margins.length != 6) {
            throw new IllegalArgumentException("margins are top, right, bottom, left (and header, footer): 4 or 6 numbers, not " + margins.length);
        }
        margins = margins == null ? null : margins.clone();
    }

    /** As the canonical constructor, not fitted to the page's width. */
    public PrintSetup(boolean landscape, int paperSize, double[] margins, String header, String footer) {
        this(landscape, paperSize, margins, header, footer, false);
    }

    @Override
    public double[] margins() {
        return margins == null ? null : margins.clone();
    }

    /** The tail of the worksheet: margins, page set-up, header and footer, in the order the schema wants. */
    String xml() {
        StringBuilder b = new StringBuilder();
        if (margins != null) {
            b.append("<pageMargins left=\"").append(inch(margins[3])).append("\" right=\"").append(inch(margins[1])).append("\" top=\"")
                    .append(inch(margins[0])).append("\" bottom=\"").append(inch(margins[2])).append("\" header=\"")
                    .append(inch(margins.length == 6 ? margins[4] : 0.2)).append("\" footer=\"").append(inch(margins.length == 6 ? margins[5] : 0.2))
                    .append("\"/>");
        }
        b.append("<pageSetup");
        if (paperSize > 0) {
            b.append(" paperSize=\"").append(paperSize).append('"');
        }
        b.append(" orientation=\"").append(landscape ? "landscape" : "portrait").append('"');
        if (fitToWidth) {
            b.append(" fitToWidth=\"1\" fitToHeight=\"0\"");
        }
        b.append("/>");
        if (header != null && !header.isEmpty() || footer != null && !footer.isEmpty()) {
            b.append("<headerFooter>");
            if (header != null && !header.isEmpty()) {
                b.append("<oddHeader>").append(Cells.esc(header)).append("</oddHeader>");
            }
            if (footer != null && !footer.isEmpty()) {
                b.append("<oddFooter>").append(Cells.esc(footer)).append("</oddFooter>");
            }
            b.append("</headerFooter>");
        }
        return b.toString();
    }

    private static String inch(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
