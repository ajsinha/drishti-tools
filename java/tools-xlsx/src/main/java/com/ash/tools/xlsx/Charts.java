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

/**
 * Native Excel charts as plain DrawingML parts: one drawing per sheet holding its charts' anchors, and one chart part per
 * {@link ChartSpec} (bar, column, line, area, pie and doughnut). Each series reads the sheet's own cells, named by its title cell.
 */
final class Charts {

    static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String C = "http://schemas.openxmlformats.org/drawingml/2006/chart";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String[] COLOURS = {"4E79A7", "F28E2B", "59A14F", "E15759", "76B7B2", "EDC948"};

    private Charts() {}

    /** The drawing part of a sheet: one anchor per chart, the n-th chart related as {@code rId<n>}. */
    static String drawing(List<ChartSpec> charts) {
        StringBuilder b = new StringBuilder(XML).append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"")
                .append(A).append("\">");
        for (int i = 0; i < charts.size(); i++) {
            ChartSpec s = charts.get(i);
            b.append("<xdr:twoCellAnchor><xdr:from><xdr:col>").append(s.anchorColumn()).append("</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>")
                    .append(s.anchorRow()).append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>")
                    .append(s.anchorColumn() + s.widthColumns()).append("</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>").append(s.anchorRow() + s.heightRows())
                    .append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"")
                    .append(i + 2).append("\" name=\"Chart ").append(i + 1).append("\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>")
                    .append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm><a:graphic><a:graphicData uri=\"").append(C).append("\">")
                    .append("<c:chart xmlns:c=\"").append(C).append("\" xmlns:r=\"").append(R).append("\" r:id=\"rId").append(i + 1)
                    .append("\"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>");
        }
        return b.append("</xdr:wsDr>").toString();
    }

    /** The chart part for {@code p}, whose data are on the sheet called {@code sheet}. */
    static String chart(ChartSpec p, String sheet) {
        String ref = Cells.quoteSheet(sheet) + "!";
        int first = p.headerRow() + 1;
        int last = p.lastRow();
        boolean round = p.kind().round();
        String tag = switch (p.kind()) {
            case LINE -> "lineChart";
            case AREA -> "areaChart";
            case PIE -> "pieChart";
            case DOUGHNUT -> "doughnutChart";
            default -> "barChart";
        };
        StringBuilder b = new StringBuilder(XML).append("<c:chartSpace xmlns:c=\"").append(C).append("\" xmlns:a=\"").append(A).append("\" xmlns:r=\"").append(R).append("\">");
        b.append("<c:roundedCorners val=\"0\"/><c:chart><c:title><c:tx><c:rich><a:bodyPr/><a:p><a:r><a:t>").append(Cells.esc(p.title()))
                .append("</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title><c:autoTitleDeleted val=\"0\"/><c:plotArea><c:layout/>");
        b.append("<c:").append(tag).append('>');
        if (tag.equals("barChart")) {
            b.append("<c:barDir val=\"").append(p.kind() == ChartSpec.Kind.BAR ? "bar" : "col").append("\"/><c:grouping val=\"clustered\"/>");
        } else if (tag.equals("lineChart") || tag.equals("areaChart")) {
            b.append("<c:grouping val=\"standard\"/>");
        }
        b.append("<c:varyColors val=\"").append(round ? 1 : 0).append("\"/>");
        String catCol = Cells.col(p.categoryColumn());
        List<Integer> series = round ? p.seriesColumns().subList(0, 1) : p.seriesColumns();
        int n = 0;
        for (int col : series) {
            String letter = Cells.col(col);
            String colour = COLOURS[n % COLOURS.length];
            b.append("<c:ser><c:idx val=\"").append(n).append("\"/><c:order val=\"").append(n).append("\"/>");
            b.append("<c:tx><c:strRef><c:f>").append(Cells.esc(ref)).append('$').append(letter).append('$').append(p.headerRow()).append("</c:f></c:strRef></c:tx>");
            if (!round) {
                b.append("<c:spPr>").append(tag.equals("lineChart") ? "<a:ln w=\"28575\"><a:solidFill><a:srgbClr val=\"" + colour + "\"/></a:solidFill></a:ln>"
                        : "<a:solidFill><a:srgbClr val=\"" + colour + "\"/></a:solidFill>").append("</c:spPr>");
            }
            if (tag.equals("barChart")) {
                b.append("<c:invertIfNegative val=\"0\"/>");
            }
            String cat = Cells.esc(ref) + "$" + catCol + "$" + first + ":$" + catCol + "$" + last;
            b.append(p.dateCategories() ? "<c:cat><c:numRef><c:f>" + cat + "</c:f></c:numRef></c:cat>" : "<c:cat><c:strRef><c:f>" + cat + "</c:f></c:strRef></c:cat>");
            b.append("<c:val><c:numRef><c:f>").append(Cells.esc(ref)).append('$').append(letter).append('$').append(first).append(":$").append(letter).append('$')
                    .append(last).append("</c:f></c:numRef></c:val>");
            if (tag.equals("lineChart")) {
                b.append("<c:smooth val=\"0\"/>");
            }
            b.append("</c:ser>");
            n++;
        }
        if (tag.equals("barChart")) {
            b.append("<c:gapWidth val=\"80\"/>");
        }
        if (tag.equals("doughnutChart")) {
            b.append("<c:firstSliceAng val=\"0\"/><c:holeSize val=\"55\"/>");
        } else if (tag.equals("pieChart")) {
            b.append("<c:firstSliceAng val=\"0\"/>");
        }
        if (!round) {
            b.append("<c:axId val=\"111111\"/><c:axId val=\"222222\"/>");
        }
        b.append("</c:").append(tag).append('>');
        if (!round) {
            boolean horizontal = p.kind() == ChartSpec.Kind.BAR;
            b.append("<c:catAx><c:axId val=\"111111\"/><c:scaling><c:orientation val=\"").append(horizontal ? "maxMin" : "minMax").append("\"/></c:scaling><c:delete val=\"0\"/>")
                    .append("<c:axPos val=\"").append(horizontal ? "l" : "b").append("\"/><c:numFmt formatCode=\"").append(p.dateCategories() ? "yyyy-mm-dd" : "General")
                    .append("\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"222222\"/>")
                    .append("<c:crosses val=\"autoZero\"/><c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>");
            b.append("<c:valAx><c:axId val=\"222222\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"0\"/><c:axPos val=\"").append(horizontal ? "b" : "l")
                    .append("\"/><c:majorGridlines/><c:numFmt formatCode=\"#,##0.##\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/>")
                    .append("<c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"111111\"/><c:crosses val=\"").append(horizontal ? "max" : "autoZero")
                    .append("\"/><c:crossBetween val=\"between\"/></c:valAx>");
        }
        b.append("</c:plotArea>");
        if (series.size() > 1 || round) {
            b.append("<c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend>");
        }
        return b.append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>").toString();
    }
}
