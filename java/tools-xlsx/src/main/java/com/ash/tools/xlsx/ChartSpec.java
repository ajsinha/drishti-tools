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

import java.util.List;
import java.util.Locale;

/**
 * A native chart beside a sheet's data ({@link Sheet#chart}): its kind and title, the column of the categories and the columns of the
 * series (zero-based), the row of the column titles (one-based: each series is named by its title cell) and the last data row, so
 * the data are rows {@code headerRow + 1} to {@code lastRow}; whether the categories are dates; and where it is drawn (a zero-based
 * column and row of its top-left corner, and its size in columns and rows). The chart reads the sheet's own cells, so it shows
 * exactly what the sheet shows.
 */
public record ChartSpec(Kind kind, String title, int categoryColumn, List<Integer> seriesColumns, int headerRow, int lastRow,
        boolean dateCategories, int anchorColumn, int anchorRow, int widthColumns, int heightRows) {

    /** The kinds drawn natively. A pie or a doughnut draws its first series only. */
    public enum Kind {
        BAR, COLUMN, LINE, AREA, PIE, DOUGHNUT;

        /** A visual's name as a kind ({@code bar}, {@code line}, {@code area}, {@code pie}, {@code donut} or {@code doughnut}); anything else is a column chart. */
        public static Kind of(String visual) {
            return switch (visual == null ? "" : visual.toLowerCase(Locale.ROOT)) {
                case "bar" -> BAR;
                case "line" -> LINE;
                case "area" -> AREA;
                case "pie" -> PIE;
                case "donut", "doughnut" -> DOUGHNUT;
                default -> COLUMN;
            };
        }

        boolean round() {
            return this == PIE || this == DOUGHNUT;
        }
    }

    public ChartSpec {
        if (kind == null || seriesColumns == null || seriesColumns.isEmpty()) {
            throw new IllegalArgumentException("a chart has a kind and at least one series");
        }
        if (headerRow < 1 || lastRow <= headerRow) {
            throw new IllegalArgumentException("a chart's data are rows after its header row: header " + headerRow + ", last " + lastRow);
        }
        if (categoryColumn < 0 || seriesColumns.stream().anyMatch(c -> c == null || c < 0) || anchorColumn < 0 || anchorRow < 0
                || widthColumns < 1 || heightRows < 1) {
            throw new IllegalArgumentException("a chart's columns and rows are 0 or more and its size at least one cell");
        }
        seriesColumns = List.copyOf(seriesColumns);
        title = title == null ? "" : title;
    }

    /**
     * A chart of the data in rows {@code headerRow + 1} to {@code lastRow}, drawn just right of its columns, level with the header
     * row, nine columns wide and twenty rows tall.
     */
    public static ChartSpec of(Kind kind, String title, int categoryColumn, List<Integer> seriesColumns, int headerRow, int lastRow) {
        int right = Math.max(categoryColumn, seriesColumns.stream().mapToInt(Integer::intValue).max().orElse(0));
        return new ChartSpec(kind, title, categoryColumn, seriesColumns, headerRow, lastRow, false, right + 2, headerRow - 1, 9, 20);
    }

    /** The same chart with date categories (shown {@code yyyy-mm-dd}). */
    public ChartSpec dates() {
        return new ChartSpec(kind, title, categoryColumn, seriesColumns, headerRow, lastRow, true, anchorColumn, anchorRow, widthColumns, heightRows);
    }

    /** The same chart drawn elsewhere: its top-left cell (zero-based column and row) and its size in columns and rows. */
    public ChartSpec at(int column, int row, int width, int height) {
        return new ChartSpec(kind, title, categoryColumn, seriesColumns, headerRow, lastRow, dateCategories, column, row, width, height);
    }
}
