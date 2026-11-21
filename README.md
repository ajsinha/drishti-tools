---
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE.
name: Drishti Tools
summary: >-
  Shared, domain-free Java utilities for the Drishti family: settings and placeholders, TLS and the outbound proxy, single flight,
  the load guard, the one .xlsx writer, and test fixtures. Written once, fixed in one place, used by Drishti and Drishti Lake.
status: 1.0.0 in preparation (no tag yet); built and tested on its own; consumers adopt from a tag (Lake in G3, Drishti in G4)
coordinates: {groupId: com.ash.tools, package: com.ash.tools.<module>, bom: com.ash.tools:tools-bom}
java: 21
modules: [tools-settings, tools-net, tools-concurrent, tools-time, tools-xlsx, tools-testkit, tools-guards]
depends-on: [the JDK only; tools-net uses tools-settings; tests use JUnit, AssertJ and Apache POI]
commands: {build: "cd java && ./mvnw clean verify"}
consumers: [Drishti Lake (G3), Drishti (G4)]
design: drishti docs/plans/DRISHTI_TOOLS_DESIGN.md (decisions TL-1 to TL-14)
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

A small workbook:

```java
try (Workbook wb = Workbook.to(out)) {                    // out is the caller's: flushed, never closed
    Sheet s = wb.sheet("Prices").freeze(1, 0).width(0, 20);
    s.row(1).text(0, "Name", "header").text(1, "Price", "header");
    s.row(2).text(0, "A", null).number(1, 12.5, "decimal");
    s.row(3).text(0, "Total", "bold").formula(1, "SUM(B2:B2)", 12.5, "number-bold");
}
```

## The rules (tools-guards)

| Guard | Rule |
|---|---|
| `ApiSurfaceTest` | `java/<module>/api-surface.txt` is each module's public API; it only grows (a removal needs a major version), and an addition updates the file in the same commit (`-Dtools.api.surface.write=true`) |
| `DomainWordsTest` | no main source holds a word of `domain-words.txt` (identifiers split at their humps); the list only grows |
| `NoProductNameTest` | no main source names a product (Drishti, Rachana, `com.ash.lake`, `DRISHTI_`, `LAKE_`) |
| `DependenciesTest` | each module's compile and runtime dependencies are its line in `dependencies.txt` (JDK-only modules: none) |
| `ModuleRulesTest` | one package per module; one module uses another only along a declared edge; no `internal` package across modules; no cycle |
| `LicenceHeaderAndLineCapTest` | the copyright header on every source; 2,500 lines at most (one constant) |
| `ExemptionsOnlyShrinkTest` | the guards' exemptions (none at 1.0.0) only shrink |

A new module needs two consumers or the owner's word, is domain-free, and takes the standard library first, a thin wrapper second,
its own code last and then with an oracle test (TL-14).

## Build

```text
cd java && ./mvnw clean verify      # JDK 21 or later; about half a minute
```

Test files are written under each module's `target/`. `tools-xlsx` also runs a million cells under a 64 MB heap in a JVM of its
own; `-Dtools.test.soffice=true` adds a LibreOffice conversion of the test workbooks when LibreOffice is installed. Until the
first tag, a consumer builds against this repository with a scratch local repository (`-Dmaven.repo.local=...`); `install` is for
a release tag only.

## Build and run in the IDE

JDK 21 as the project SDK, and **Maven, Runner, JRE: Use Project JDK**.

| IDE | What runs what |
|---|---|
| **IntelliJ IDEA** (open `java/pom.xml`) | `java/run-configs/`, folder **Tools**: **Tools build all** (no tests), **Tools test** (the full build with every test and guard), **Tools API surface rewrite** (after a reviewed API addition), **Tools xlsx with LibreOffice**, **Tools install release** (a release tag only). Any test class runs from its gutter icon |
| **VS Code** | **File, Open Workspace from File**, `drishti-tools.code-workspace`: the same Maven tasks; tests from the Testing view. Recommended: Extension Pack for Java |

There is no program to run: the modules are libraries. PyCharm is not needed (no Python here).

## Documents

| Document | What |
|---|---|
| [docs/ORIGINS.md](docs/ORIGINS.md) | where each class came from, every difference between the copies and how it was resolved, the xlsx writers folded |
| [CHANGELOG.md](CHANGELOG.md) | what changed when |
| Drishti `docs/plans/DRISHTI_TOOLS_DESIGN.md` | the design and the decisions TL-1 to TL-14 |
