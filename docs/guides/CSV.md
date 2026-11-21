<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-csv: RFC 4180 reader and writer

Package `com.ash.tools.csv`; JDK only (Apache Commons CSV is the test oracle, both ways).

`CsvReader` reads one record at a time (`next()` returns `null` at the end; `CsvReader.parse(text)` returns every record): fields separated by
the delimiter (default comma), a field in double quotes may hold the delimiter, line breaks and doubled quotes; records end at CRLF, LF or
CR; a byte-order mark at the start is dropped; a blank line is a record of one empty field (`CsvReader.blank`). It is lenient as
spreadsheets are: text after a closing quote is kept, a quote left open runs to the end of the input.

`CsvWriter.to(appendable)` writes records (`row(...)`): a field is quoted when it holds the delimiter, a quote, CR or LF; records end with
CRLF (`lineEnd` changes it); `null` is an empty field and any other value its `String.valueOf` (format numbers before). Options:
`delimiter(char)`, `bom()` (spreadsheets then read UTF-8), and `formulaGuard(true)`, which gives text a spreadsheet would run as a
formula (first character `= + - @`, tab or CR, and not a number) a leading apostrophe so it stays text. The writer never closes or flushes
your `Appendable`.

## Tutorial: write, then read back

```java
import com.ash.tools.csv.*;
import java.util.*;

public class CsvEx {
  public static void main(String[] a) {
    StringBuilder out = new StringBuilder();
    CsvWriter.to(out).formulaGuard(true)
        .row("id", "note")
        .row(1, "plain")
        .row(2, "has, comma and \"quote\"")
        .row(3, "=SUM(A1:A9)");
    System.out.print(out.toString().replace("\r\n", "\n"));
    // id,note
    // 1,plain
    // 2,"has, comma and ""quote"""
    // 3,'=SUM(A1:A9)
    List<List<String>> rows = CsvReader.parse(out.toString());
    System.out.println(rows.get(2));   // [2, has, comma and "quote"]
  }
}
```

Build and class path: [BUILD_AND_IDE.md](../BUILD_AND_IDE.md).
