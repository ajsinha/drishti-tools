<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# Where the code came from

Read from git only: Drishti `main` at `9ce7c3170`, Drishti Lake `origin/main` at `c715d15`. The files were taken **with their
history** from Drishti (`git filter-repo` over a scratch clone, each file's earlier paths followed, commit messages kept; Lake's
copies of the same files have 11 commits of history, all after they were copied from Drishti, so Drishti's is the fuller one).
Both copies were then compared line by line (package names removed); this page lists every difference and how it was resolved.
Neither Drishti nor Lake was changed: each adopts from a tag (Lake in G3, Drishti in G4).

## The copies in Drishti and Lake

The rule for every difference: **a product's name becomes a parameter the product passes**, and the wording of both copies becomes
product-free. Nothing else differed.

| Class | Differing lines | The difference | Resolved as |
|---|---|---|---|
| `TlsContexts` | 4 | logger `com.ash.drishti.tls` / `com.ash.lake.io.net.tls`; the key-check challenge text `drishti-tls-check` / `lake-tls-check`; the opt-in variable in the refusal | logger `com.ash.tools.net.tls`; challenge `tls-key-check` (signed and verified in memory only, no effect); `build(s, env, clock, allowInsecureVariable)` added, the three-argument form keeps working with the default `ALLOW_INSECURE_TLS` |
| `TlsSettings` | 9 | `ALLOW_INSECURE_ENV` = `DRISHTI_ALLOW_INSECURE_TLS` / `LAKE_ALLOW_INSECURE_TLS`, also in the form's description | the host names its variable: `settingSpecs(prefix, variable)`, `TlsContexts.build(..., variable)`, `TlsHttp.material(..., variable)`; default `ALLOW_INSECURE_TLS` |
| `ProxySettings` | 19 | messages name `drishti.http.proxy.` / `site proxy.`; comments name Drishti's keys | `SiteProxy.configure(settings, fromEnvironment, prefix)`: the host's key in messages; default `site proxy.` (Lake's); Lake's wording |
| `SiteProxy` | 1 | comment names `drishti.http.proxy.*` in `application.yaml` | Lake's wording |
| `TlsMaterial` | 2 | examples `CN=drishti` / `CN=lake` | `CN=client` |
| `Placeholders` | 14 | comments only (Drishti's file list, `-Ddrishti.settings.file` / `-Dlake.settings.file`) | product-free comments; the "not set" message names `SettingsFile.property()`, the property the host applied |
| `SettingsFile` | 29 | `PROPERTY` = `drishti.settings.file` / `lake.settings.file`; `${drishti.home}` / `${lake.home}` resolved in values; Drishti's method `applyFromSystemProperty()`; comments | `applyFrom(property)` and `applyFrom(property, homeProperty)`; messages name the property applied; `property()` added |
| `LoadGuard` | 17 | Drishti reads `DRISHTI_BUSINESS_ZONE` (`fromArgs(args)`), Lake takes its runtime's zone (`fromArgs(args, zone)`); Drishti's `DEFAULT_ZONE` | both kept: `fromArgs(args, zone[, clock])` and `fromArgs(args, envPrefix)` / `fromArgs(args, env, envPrefix, clock)`; `DEFAULT_ZONE` kept |
| `SingleFlight`, `SettingSpec`, `Secrets`, `PemReader`, `Der`, `CertInfo`, `TlsException`, `NoProxy`, `ProxyException`, `FakeProxy`, `TestPki` | 0 to 2 | the file header only | one header |
| `TlsFixture` | 2 | header; names `drishti-tls`, `drishti-test-ca`, the client certificate and keystore alias `drishti` | `tls-fixture`, `test-ca`, `client` (a consumer's test that reads the alias `drishti` reads `client`) |
| `Roots` / `LakeRoots` (`anchorValue`, `isAbsolute`) | 93 in all (the products' own roots); 1 in the shared rule | the shared rule is the same; Drishti keeps the package registry's URL as a path; the home setting named in messages | the shared rule is `PathAnchors`, the home setting a parameter; the registry exception and the lists of named roots stay in each product |
| `TlsHttp` | (Drishti only) | | moved as it is; `material(..., variable)` added |

`Secrets` moved to `tools-settings` (it reads settings, not TLS), so it throws `SettingException` instead of `TlsException`;
`TlsSettings.parse` wraps it in a `TlsException` with the same message, and `ProxySettings` turns it into a `ProxyException` as
before, so callers see what they saw. Its `secret(get, prefix, key)` and `expand` became public for `tools-net`.

Not taken (design 1.1): `TempFiles` (the copies have diverged, 229 lines: `tools-files` in i8), the archive readers (Lake's), Rachana's
`Sha384`, `Rlib`, `ViewJson` (stay in Rachana core).

## The four xlsx writers, folded into one

| Drishti file | Lines | Became |
|---|---|---|
| `server/playground/WorkbookStream.java` | 198 | `Workbook` (the streaming shape all four now use) |
| `server/playground/SheetStream.java` | 249 | `Sheet` |
| `server/dist/XlsxWriter.java` | 378 | `Cells` (its helpers), `PrintSetup`, `Styles`; the export document's layout (About sheet, sections, widths, notes) stays in Drishti as an adapter |
| `server/dist/XlsxCharts.java` | 170 | `Charts` + `ChartSpec`; the export's policy (500 points, 6 series, masked columns never plotted) stays in the adapter |
| `server/dist/XlsxFormulas.java` | 125 | `Formulas` (+ `problem`) |
| `bicompute/export/Xlsx.java` | 241 | folded into `Sheet` (outline levels, summary row position) and `Workbook`; deleted |
| `server/capacity/CapacityWorkbook.java` | 219 | `WorkbookTemplate` (fill, set and add cells, widths, write); the capacity layout stays in Drishti |

Where the writers behaved differently, one behaviour was chosen:

| Behaviour | Before | Now | Why |
|---|---|---|---|
| A sheet name in a chart's references | quoted, not XML-escaped (a name with `&` broke the chart part) | quoted and escaped | a bug, fixed once |
| Sheet names | made valid and unique by the exports only | every writer | an invalid name corrupts the file |
| Formula policy | the pivot export's `SUM` formulas were not checked | every formula checked against the shared function set | one rule |
| A formula whose result is NaN | `#NUM!` (exports, playgrounds) or no cached value (pivot) | `#NUM!` | the truth, readable without recalculation |
| A text formula with no cached text | an empty value or none | none | a reader recalculates rather than showing a wrong empty text |
| A NaN number | a styled empty cell (playgrounds) or nothing | a styled empty cell when styled, else nothing | keeps a band's fill |
| Number text | the same `num` twice; the capacity workbook used `BigDecimal.toPlainString` | `Cells.num` (whole numbers without a fraction, else the shortest round-trip text) | one rule; the same values read back |
| Column letters, escaping | two implementations each | one each | identical results |
| Styles | four `styles.xml` (export header fill `EEF1F5`, pivot `DCE6F1`; title 16 or 14 point; note grey or grey italic) | one standard look (the exports'), named styles; any program passes its own `Styles` | a house style replaces the look without code |
| The selected tab | the sheet named `Main` (playgrounds), none elsewhere | the first sheet | no name in the writer |
| Default row height | 14 (playgrounds) | 15 (Excel's) | |
| Zip entry times | the current time | one fixed time | byte-identical output for the same input |
| Compression | the default level or the fastest | the fastest | streaming large books |
| Branding footer | read from the template's values inside `SheetStream` | the host builds the footer into `PrintSetup` | no product data in the writer |
| Document properties | the playground template's parts | `Workbook.properties(core, app)` | |
| `{{KEY}}` filling and comment dropping | in `PlaygroundTemplate.part` and `CapacityWorkbook` | `WorkbookTemplate.fill` | one place |

The proof (tests): every feature written and read back by Apache POI cell by cell, POI's formula results equal to the cached ones,
the layout (panes, widths, merges, drop-downs, conditional formats, print set-up, names, outline, tab colour), byte-identical
output twice, charts found by POI with their series, a template's cells and live formulas, a million cells under a 64 MB heap,
and (on request) LibreOffice converting the books. G4 adds the before-and-after evidence: Drishti's frozen exports and playground
books written on the old code and on this module, compared cell by cell and formula by formula.
