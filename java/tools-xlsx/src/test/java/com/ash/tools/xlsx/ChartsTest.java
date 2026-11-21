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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Native charts: POI finds each chart beside its data, with its title and its series reading the sheet's own cells. */
class ChartsTest {

    private static byte[] book(ChartSpec... charts) {
        return Workbook.bytes(wb -> {
            Sheet s = wb.sheet("Amounts by group");
            s.row(1).text(0, "Group", "header").text(1, "Amount", "header").text(2, "Rate", "header");
            s.row(2).text(0, "A", null).number(1, 1500, "int").number(2, 2.5, "decimal");
            s.row(3).text(0, "B", null).number(1, 900, "int").number(2, 1.25, "decimal");
            for (ChartSpec c : charts) {
                s.chart(c);
            }
            Sheet d = wb.sheet("By date");
            d.row(1).text(0, "Date", "header").text(1, "Amount", "header");
            d.row(2).date(0, LocalDate.of(2026, 10, 8), null).number(1, 10, null);
            d.row(3).date(0, LocalDate.of(2026, 10, 9), null).number(1, 12, null);
            d.chart(ChartSpec.of(ChartSpec.Kind.LINE, "Over time", 0, List.of(1), 1, 3).dates());
        });
    }

    @Test
    void everyKindIsAChartPoiReadsWithItsSeries() throws Exception {
        for (ChartSpec.Kind kind : ChartSpec.Kind.values()) {
            byte[] x = book(ChartSpec.of(kind, "Amount by group", 0, List.of(1, 2), 1, 3));
            Map<String, String> parts = Oracle.parts(x);
            assertThat(parts).containsKeys("xl/worksheets/_rels/sheet1.xml.rels", "xl/drawings/drawing1.xml", "xl/drawings/_rels/drawing1.xml.rels",
                    "xl/charts/chart1.xml", "xl/charts/chart2.xml", "xl/drawings/drawing2.xml");
            String chart = parts.get("xl/charts/chart1.xml");
            assertThat(chart).contains("<c:f>'Amounts by group'!$B$2:$B$3</c:f>").contains("<c:f>'Amounts by group'!$A$2:$A$3</c:f>");
            try (XSSFWorkbook wb = Oracle.open(x)) {
                List<XSSFChart> charts = wb.getSheetAt(0).getDrawingPatriarch().getCharts();
                assertThat(charts).as(kind.name()).hasSize(1);
                XSSFChart c = charts.get(0);
                assertThat(c.getTitleText().getString()).isEqualTo("Amount by group");
                int series = c.getChartSeries().stream().mapToInt(d -> d.getSeriesCount()).sum();
                assertThat(series).as(kind.name()).isEqualTo(kind == ChartSpec.Kind.PIE || kind == ChartSpec.Kind.DOUGHNUT ? 1 : 2);
                assertThat(wb.getSheetAt(1).getDrawingPatriarch().getCharts()).hasSize(1);
            }
        }
    }

    @Test
    void theKindsXmlAndDateCategories() {
        assertThat(Oracle.parts(book(ChartSpec.of(ChartSpec.Kind.BAR, "t", 0, List.of(1), 1, 3))).get("xl/charts/chart1.xml")).contains("<c:barDir val=\"bar\"/>");
        assertThat(Oracle.parts(book(ChartSpec.of(ChartSpec.Kind.COLUMN, "t", 0, List.of(1), 1, 3))).get("xl/charts/chart1.xml")).contains("<c:barDir val=\"col\"/>");
        Map<String, String> parts = Oracle.parts(book(ChartSpec.of(ChartSpec.Kind.PIE, "t", 0, List.of(1, 2), 1, 3)));
        assertThat(parts.get("xl/charts/chart1.xml")).contains("<c:pieChart>").doesNotContain("$C$");
        assertThat(parts.get("xl/charts/chart2.xml")).contains("<c:lineChart>").contains("<c:cat><c:numRef>").contains("yyyy-mm-dd");
        assertThat(ChartSpec.Kind.of("donut")).isEqualTo(ChartSpec.Kind.DOUGHNUT);
        assertThat(ChartSpec.Kind.of("treemap")).isEqualTo(ChartSpec.Kind.COLUMN);
    }

    @Test
    void twoChartsOnOneSheetShareItsDrawingAndAreNumberedAcrossTheBook() throws Exception {
        byte[] x = book(ChartSpec.of(ChartSpec.Kind.COLUMN, "one", 0, List.of(1), 1, 3),
                ChartSpec.of(ChartSpec.Kind.LINE, "two", 0, List.of(2), 1, 3).at(4, 22, 6, 12));
        Map<String, String> parts = Oracle.parts(x);
        assertThat(parts).containsKeys("xl/charts/chart1.xml", "xl/charts/chart2.xml", "xl/charts/chart3.xml");
        assertThat(parts.get("xl/drawings/drawing1.xml")).contains("<xdr:row>22</xdr:row>").contains("r:id=\"rId2\"");
        try (XSSFWorkbook wb = Oracle.open(x)) {
            assertThat(wb.getSheetAt(0).getDrawingPatriarch().getCharts()).extracting(c -> c.getTitleText().getString()).containsExactly("one", "two");
        }
    }

    @Test
    void aChartSpecRefusesWhatCannotBeDrawn() {
        assertThatThrownBy(() -> ChartSpec.of(ChartSpec.Kind.LINE, "t", 0, List.of(), 1, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChartSpec.of(ChartSpec.Kind.LINE, "t", 0, List.of(1), 3, 3)).hasMessageContaining("rows after its header row");
    }
}
