<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# Build, install and the IDE

The one build guide of this repository. The modules are libraries: there is no program to start.

## Prepare your environment

| Need | Version | Check |
|---|---|---|
| JDK | 21 or later | `java -version` |
| Git | any | `git --version` |
| LibreOffice (optional) | any | only for the xlsx conversion test (`-Dtools.test.soffice=true`) |

Maven is not installed: `java/mvnw` (Windows `java\mvnw.cmd`) downloads it. Python is not used here.

## Build

```text
git clone <repository> drishti-tools && cd drishti-tools/java
./mvnw clean verify                 # every module, every test and guard; about half a minute
./mvnw clean verify -DskipTests     # compile and package only
./mvnw clean verify -pl tools-time -am     # one module and what it needs
```

Test files are written under each module's `target/`; Maven's own temporary folder is `java/build-tmp/` (set by `TOOLS_MAVEN_TMP`
when you want another place). `tools-xlsx` also runs a million cells under a 64 MB heap in a JVM of its own.

## Install order and where the artifacts go

The Drishti family installs in this order: **Rachana (tag) -> Drishti Tools (tag) -> Drishti Lake (tag) -> Drishti**. A consumer builds
against a *released* tag, never a branch:

```text
git checkout vX.Y.Z                       # the release tag
cd java && ./mvnw clean install -DskipTests
```

The jars land in `~/.m2/repository/com/ash/tools/` (Windows `C:\Users\<user>\.m2\repository\com\ash\tools\`). Until a tag is installed, a
consumer can build against a working tree with a scratch repository:

```text
./mvnw install -DskipTests -Dmaven.repo.local=/path/to/scratch-m2
export MAVEN_ARGS="-Dmaven.repo.local=/path/to/scratch-m2 -Dmaven.repo.local.tail=$HOME/.m2/repository"   # the consumer's build reads it
```

Never `install` from a working tree into `~/.m2`.

## IntelliJ IDEA

Open the repository root as a project and link `java/pom.xml` in the Maven tool window. Set the project SDK to JDK 21 and **Settings, Build Tools, Maven, Runner, JRE: Use Project JDK**.
Run configurations are in `run-configs/` at the repository root, folder **Tools** (open the repository root as the project and link `java/pom.xml` in the Maven tool window):

| Configuration | Runs |
|---|---|
| **Tools: build all** | the build without tests |
| **Tools: test** | the full build with every test and guard |
| **Tools: maintain API surface (rewrite)** | the build with `-Dtools.api.surface.write=true`, after a reviewed API addition |
| **Tools: check guards** | only the guards module and what it needs (the launchers are checked too) |
| **Tools: test xlsx with LibreOffice** | the xlsx module with the LibreOffice conversion test |
| **Tools: install release (tag only)** | `install`; on a release tag only |

Any test class runs from its gutter icon. IntelliJ Community has no Node support; nothing here needs it.

## PyCharm

Not needed: there is no Python in this repository.

## VS Code

**File, Open Workspace from File**, choose `drishti-tools.code-workspace` (a visible file at the repository root; no hidden folder). Install the
recommended Extension Pack for Java. Edit the JDK path in the workspace's `java.configuration.runtimes` (Windows `C:\jdk-21`). **Terminal,
Run Task** has the same five tasks as the IntelliJ configurations; tests run from the Testing view.

## Windows and company networks: `PKIX path building failed`

A company network that re-signs HTTPS with its own root certificate stops Maven on its first download with
`unable to find valid certification path`. Windows trusts that root; the JDK does not. One VM option fixes it, with no file change and no
administrator rights:

```text
-Djavax.net.ssl.trustStoreType=Windows-ROOT
```

In IntelliJ: **Maven, Runner, VM Options** and **Maven, Importing, VM options for importer**. In a terminal:
`setx MAVEN_OPTS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"`, then open a new terminal. Alternatives: your company's mirror in
`%USERPROFILE%\.m2\settings.xml`, or the root added to the JDK with `keytool -importcert -cacerts`. No executable has to be placed on
the machine: the build is Java only.

## The repository's rules, as tests

`tools-guards` runs with the build: the API surface of each module only grows (`api-surface.txt`); no domain word and no product name in
main sources; each module's dependencies are its line of `dependencies.txt`; one package per module and no cycle; the copyright header
on every source and 2,500 lines at most per file. A new public method updates `api-surface.txt` in the same commit (**Tools API surface
rewrite**).
