---
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE.
name: Drishti Tools
summary: >-
  Shared, domain-free Java utilities for the Drishti family: settings and placeholders, TLS and the outbound proxy, single flight,
  the load guard, the one .xlsx writer, and test fixtures. Written once, fixed in one place, used by Drishti and Drishti Lake.
status: built and tested on its own; the platform build installs it into the Maven repository for Drishti
coordinates: {groupId: com.ash.tools, package: com.ash.tools.<module>, bom: com.ash.tools:tools-bom}
java: 21
modules: [tools-settings, tools-net, tools-concurrent, tools-time, tools-xlsx, tools-codec, tools-csv, tools-files, tools-testkit, tools-guards]
depends-on: [the JDK only; tools-net uses tools-settings; tests use JUnit, AssertJ and Apache POI]
commands: {build: "cd java && ./mvnw clean verify"}
consumers: [Drishti Lake, Drishti]
docs: [docs/BUILD_AND_IDE.md, docs/GUIDE.md]
licence: proprietary (LICENSE)
---

# Drishti Tools

**Shared, domain-free Java utilities for the Drishti family.**

Code that Drishti and Drishti Lake each held a copy of, and the four ways Drishti wrote an Excel workbook, made one library: less
code, one place to fix a bug, small modules that take only what they need. Nothing here knows a business domain or a product: a
product's property names, variable names and folders are parameters it passes. Drishti Elements (JavaScript) is not a consumer.

Author: Ashutosh Sinha. Proprietary. All rights reserved (see [LICENSE](LICENSE)).

## Modules

| Module | Package | What it does | Depends on |
|---|---|---|---|
| `tools-settings` | `com.ash.tools.settings` | `Placeholders` (`${key}`, `${key:default}`), `SettingsFile` (the ini files named by a system property the program chooses), `SettingSpec`, `Secrets` (`${ENV}` and `-file` twins), `PathAnchors` (absolute, `${root}/rest`, never the working folder), `SettingException` | JDK |
| `tools-net` | `com.ash.tools.net` | TLS for the JDK's HTTP client (PEM, PKCS12, JKS, the JVM's authorities; mutual TLS; expiry warnings): `TlsSettings`, `TlsContexts`, `TlsMaterial`, `TlsHttp`; the outbound proxy: `ProxySettings`, `NoProxy`, `SiteProxy` | JDK, `tools-settings` |
| `tools-concurrent` | `com.ash.tools.concurrent` | `SingleFlight`: one load per key at a time | JDK |
| `tools-time` | `com.ash.tools.time` | `LoadGuard`: no business date in the future, retention from the load's newest date, no mass drop by accident | JDK |
| `tools-xlsx` | `com.ash.tools.xlsx` | the one .xlsx writer: `Workbook`, `Sheet` (streaming, constant memory), `Styles`, `PrintSetup`, `ChartSpec` (native charts), `Formulas` (the function set Excel, LibreOffice and Google Sheets share), `WorkbookTemplate` (fill a finished workbook), `Cells` | JDK (Apache POI is its test oracle only) |
| `tools-codec` | `com.ash.tools.codec` | `Hashes` (digests as lower-case hex, text as UTF-8, files as streams, no checked exception), `CanonicalJson` (plain data as JSON text with every map in key order: the same bytes as Jackson's `ORDER_MAP_ENTRIES_BY_KEYS`) | JDK (Jackson is its test oracle only) |
| `tools-csv` | `com.ash.tools.csv` | RFC 4180: `CsvReader` (streaming; quotes, doubled quotes, line breaks in quotes, a byte-order mark dropped), `CsvWriter` (quotes only where needed, CRLF, optional byte-order mark and formula guard) | JDK (Apache Commons CSV is its test oracle only) |
| `tools-files` | `com.ash.tools.files` | `TempFolder`: one owner's temporary folder under a base it names, marked, removed on a clean stop; stale folders listed, never removed | JDK |
| `tools-testkit` | `com.ash.tools.testkit` | `FakeProxy` (a local forward proxy), `TlsFixture` and `TestPki` (a throw-away PKI) | JDK |
| `tools-bom` | | one version for every module | |
| `tools-guards` | | the repository's rules, as tests (below); not published | |

A consumer imports the BOM and names modules without versions:

```xml
<dependencyManagement><dependencies>
  <dependency><groupId>com.ash.tools</groupId><artifactId>tools-bom</artifactId><version>${drishti.tools.version}</version>
    <type>pom</type><scope>import</scope></dependency>
</dependencies></dependencyManagement>
```

Each module has a section with a tutorial that was run in [docs/GUIDE.md](docs/GUIDE.md).

## The rules (tools-guards)

| Guard | Rule |
|---|---|
| `ApiSurfaceTest` | `java/<module>/api-surface.txt` is each module's public API; it only grows (a removal needs a major version), and an addition updates the file in the same commit (`-Dtools.api.surface.write=true`) |
| `DomainWordsTest` | no main source holds a word of `domain-words.txt` (identifiers split at their humps); the list only grows |
| `NoProductNameTest` | no main source names a product (Drishti, Rachana, `com.ash.lake`, `DRISHTI_`, `LAKE_`) |
| `DependenciesTest` | each module's compile and runtime dependencies are its line in `dependencies.txt` (JDK-only modules: none) |
| `ModuleRulesTest` | one package per module; one module uses another only along a declared edge; no `internal` package across modules; no cycle |
| `LicenceHeaderAndLineCapTest` | the copyright header on every source; 2,500 lines at most (one constant) |
| `ExemptionsOnlyShrinkTest` | the guards' exemptions only shrink |
| `AutomaticModuleNameTest` | every module declares `Automatic-Module-Name` `com.ash.tools.<module>` |

A new module needs two consumers or the owner's word, is domain-free, and takes the standard library first, a thin wrapper second,
its own code last and then with an oracle test.

## Where it fits the Drishti platform

Drishti Tools is the first product of the platform build. The platform's `build_all.py` (and its IntelliJ launcher `platform-clean-install`)
builds the products in order, **Drishti Tools -> Drishti Rachana -> Drishti Lake -> Drishti**, and installs each one's jars into the local
Maven repository (`~/.m2/repository/com/ash/tools/`, Windows `C:\Users\<user>\.m2\repository\com\ash\tools\`) before the next builds against
them. Drishti Rachana's server and Drishti Lake use Tools; Drishti Elements (JavaScript) does not. A consumer always builds against a
released tag, never a branch.

## Build and test

JDK 21 or later and Git; Maven is not installed (the wrapper `java/mvnw`, Windows `java\mvnw.cmd`, downloads it); no Python here. In `java/`:
`./mvnw clean verify` builds every module and runs every test and guard (about half a minute). Install from a release tag, the scratch
repository for a consumer, IntelliJ and VS Code launchers (`java/run-configs/`, `drishti-tools.code-workspace`) and the Windows
`PKIX path building failed` fix are in [docs/BUILD_AND_IDE.md](docs/BUILD_AND_IDE.md). The modules are libraries: there is no program to run.

## Documents

| Document | What |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | one section per module, each with a tutorial that was run |
| [docs/BUILD_AND_IDE.md](docs/BUILD_AND_IDE.md) | prepare, build, install from a tag, IntelliJ, VS Code, Windows |
| [CHANGELOG.md](CHANGELOG.md) | what changed when |
