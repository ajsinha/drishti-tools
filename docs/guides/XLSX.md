<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-xlsx: the one .xlsx writer

Package `com.ash.tools.xlsx`; JDK only (Apache POI is the test oracle, never a dependency). An Excel workbook (ECMA-376 SpreadsheetML)
written with the JDK's zip, straight into your stream, sheet after sheet and row after row, so a workbook of a million cells is never
held whole (a test writes one under a 64 MB heap). The same input gives the same bytes.

| Class | Job |
|---|---|
| `Workbook` | `Workbook.to(out)` (the stream is flushed, never closed), `sheet(name)`, `properties`, `name` (defined names), `recalculateOnOpen`; `Workbook.bytes(consumer)` for an in-memory file |
| `Sheet` | the head (`freeze`, `width`, `widths`, `tab`, `outline`, `print`, `repeatRows`) before the first row; then `row(n)` and cells left to right: `text`, `number`, `date`, `bool`, `blank`, `value`, `formula`, `formulaText`; `merge`, `list` (a drop-down), `checks` (pass/fail colouring), `chart` |
| `Styles` | the look by **name**: `Styles.standard()` has `normal`, `header`, `int`, `decimal`, `date`, `title`, `note`, `bold`, `number`, `number-bold`, `percent`, and `pass` / `fail` for conditional formats; `Styles.of(...)` takes a house `styles.xml` |
| `Formulas` | `check` / `problem` / `scan`: only functions that Excel, LibreOffice Calc and Google Sheets share; no dynamic arrays (`FILTER`, `SORT`, `LET`, `XLOOKUP`...), no `INDIRECT` or `OFFSET`, no structured references or macros. `Sheet.formula` checks every formula it writes |
| `ChartSpec` | a native chart over the sheet's own cells: `COLUMN`, `BAR`, `LINE`, `AREA`, `PIE`, `DOUGHNUT` |
| `PrintSetup` | orientation, paper, margins, header and footer, fit to one page wide |
| `WorkbookTemplate` | fill a finished workbook made in Excel: `{{KEY}}` placeholders, `setCell`, `addCell`, `width`; its formulas stay live |
| `Cells` | helpers: column letters, references, serial dates, valid and unique sheet names |

Text is always text, whatever it starts with (a cell starting `=` is not a formula). A formula carries its cached result, so a reader that
never recalculates (a viewer, a parser) shows numbers; `Workbook.recalculateOnOpen()` asks Excel to recalculate anyway.

## Tutorial: a priced sheet with a total and a chart

```java
import com.ash.tools.xlsx.*;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

public class XlsxEx {
  public static void main(String[] a) throws Exception {
    try (OutputStream os = Files.newOutputStream(Path.of(a[0])); Workbook wb = Workbook.to(os)) {
      Sheet s = wb.sheet("Prices").freeze(1, 0).width(0, 20);
      s.row(1).text(0, "Name", "header").text(1, "Price", "header").text(2, "Date", "header");
      s.row(2).text(0, "A", null).number(1, 12.5, "decimal").date(2, LocalDate.of(2026, 11, 20), "date");
      s.row(3).text(0, "B", null).number(1, 7.5, "decimal");
      s.row(4).text(0, "Total", "bold").formula(1, "SUM(B2:B3)", 20.0, "number-bold");
      s.chart(ChartSpec.of(ChartSpec.Kind.COLUMN, "Prices", 0, List.of(1), 1, 3).at(4, 5, 6, 12));
    }
    System.out.println(Formulas.problem("SUM(A1:A3)"));        // null: allowed
    System.out.println(Formulas.problem("INDIRECT(A1)"));      // functions outside the Excel, LibreOffice and Google Sheets common set [INDIRECT] in INDIRECT(A1)
  }
}
```

`java -cp <tools-xlsx classes> XlsxEx.java /tmp/prices.xlsx` writes a workbook that opens in Excel, LibreOffice and Google Sheets. To check
it with LibreOffice as well, run the module's test with `-Dtools.test.soffice=true` (IntelliJ configuration **Tools xlsx with LibreOffice**,
[BUILD_AND_IDE.md](../BUILD_AND_IDE.md)); `Formulas.scan(bytes)` lists every disallowed formula of any finished file, for a release check.
