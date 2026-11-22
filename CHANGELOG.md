<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# Changelog

Entries are written per release; unreleased work is listed first.

## 1.2.0 (unreleased)

- `tools-codec` `Zips`: deterministic zip writing (`write`, `toBytes`: entries sorted, every timestamp fixed, same content gives the same bytes), a streaming `Writer` (duplicate and unsafe names refused), and reading (`read`, `extract`) that refuses zip-slip names (`safeName`) and stops at a byte cap. JDK only.
- `tools-net` `HttpClients`: the one `HttpClient` builder, with the proxy (`ProxySettings`) and TLS material (`TlsHttp`) applied and a connect timeout (default 10 s); `forTarget(settings, url, urlSetting)` decides both from settings; no global state.
- `tools-files` `JvmTemp`: the `-Djava.io.tmpdir` check (`passed`), its warning, `ensureExists`, `sweep` of stale native-library copies in a folder the owner names (name pattern a parameter), and `copyNative` (atomic, removed at exit and with the `TempFolder`).
- `tools-guards` kit: `HeaderAndLineCap.withoutHeader` takes the header mark (and the line window) as parameters; the one-argument form keeps the old mark.
- API-surface baselines of the three modules grow (additive).

## Unreleased

- **IDE launchers follow the platform standard**: one `run-configs/` at the repository root, every launcher named `Tools: <verb> <what>` (IntelliJ folder `Tools`), the same names as the VS Code tasks and launches; missing launchers added; the check `LaunchersTest` (a test) keeps names, files and workspace in step.

## 1.1.0 (2026-11-21)

- Automatic-Module-Name on every module (com.ash.tools.<module>)

## 1.0.0 (2026-11-21)

- The repository: Maven reactor `com.ash.tools` (JDK 21, `mvnw`), `tools-bom`, the guards (`tools-guards`), LICENSE, IDE launchers.
- `tools-settings`, `tools-net`, `tools-concurrent`, `tools-time`, `tools-testkit`: from Drishti `main` `9ce7c3170` with history,
  compared with Lake's copies; every place a product's name was written is a parameter (docs/ORIGINS.md).
- `tools-settings`: `PathAnchors` (new), `SettingException` (new; `Secrets` throws it, TLS rewraps it as before).
- `tools-xlsx`: one streaming writer replacing Drishti's four (the exports' `XlsxWriter` with charts and formulas, the pivot
  export's `Xlsx`, the playgrounds' `WorkbookStream`/`SheetStream`, the capacity workbook), proven with Apache POI as the oracle.
  Fixed on the way: a chart's sheet reference is XML-escaped (a sheet named with `&` broke the chart part); every sheet name is made
  valid and unique; the pivot export's formulas are checked against the common function set; a formula's NaN result is cached as
  `#NUM!` everywhere; the same input gives the same bytes.
- `tools-codec` (`Hashes`, `CanonicalJson`), `tools-csv` (`CsvReader`, `CsvWriter`), `tools-files` (`TempFolder`): the extras that
  remove copies that exist today (i7b duplicate audit T3 to T6). `CanonicalJson` writes Jackson's `ORDER_MAP_ENTRIES_BY_KEYS` text
  (Jackson is the test oracle); the CSV pair is RFC 4180 with Commons CSV as the oracle; `TempFolder` is Drishti's and Lake's
  `TempFiles` without their settings (the owner names the base and the folder). Lake adopts `Hashes` and `TempFolder` now, Drishti in G4.
- `tools-guards` is published as a test jar (`tools-guards:test-jar`, in the BOM): its `kit` package (`GuardFiles`, `HeaderAndLineCap`, `ApiSurface`) is the one copy of the header, line cap and API-surface checks Lake and Drishti call (i7b duplicate audit T10); the repository's own lists stay out of the jar.
