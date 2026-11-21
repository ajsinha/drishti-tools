<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; unreleased work is listed first.

## Unreleased (1.0.0)

- The repository: Maven reactor `com.ash.tools` (JDK 21, `mvnw`), `tools-bom`, the guards (`tools-guards`), LICENSE, IDE launchers.
- `tools-settings`, `tools-net`, `tools-concurrent`, `tools-time`, `tools-testkit`: from Drishti `main` `9ce7c3170` with history,
  compared with Lake's copies; every place a product's name was written is a parameter (docs/ORIGINS.md).
- `tools-settings`: `PathAnchors` (new), `SettingException` (new; `Secrets` throws it, TLS rewraps it as before).
- `tools-xlsx`: one streaming writer replacing Drishti's four (the exports' `XlsxWriter` with charts and formulas, the pivot
  export's `Xlsx`, the playgrounds' `WorkbookStream`/`SheetStream`, the capacity workbook), proven with Apache POI as the oracle.
  Fixed on the way: a chart's sheet reference is XML-escaped (a sheet named with `&` broke the chart part); every sheet name is made
  valid and unique; the pivot export's formulas are checked against the common function set; a formula's NaN result is cached as
  `#NUM!` everywhere; the same input gives the same bytes.
