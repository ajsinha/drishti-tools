<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# Module guides

One section per module, each with a tutorial that was run. Class path for the tutorials: the module's `target/classes` from [BUILD_AND_IDE.md](BUILD_AND_IDE.md).

## tools-settings: settings, placeholders, secrets and path anchors

Package `com.ash.tools.settings`; depends on the JDK only. Four small classes that every program of the family reads its configuration
through, so that a key means the same thing everywhere.

| Class | Job |
|---|---|
| `Placeholders` | resolves `${key}` and `${key:default}` in any text |
| `SettingsFile` | optional `.ini` files named by a system property you choose; their entries become system properties |
| `Secrets` | a secret as `${ENV_NAME}` or as a `<key>-file` twin; never a literal in a document |
| `PathAnchors` | makes a path setting absolute: absolute as given, or anchored at a named root; never the working folder |
| `SettingSpec` | one setting's description (name, type, required, default, secret, group) for forms and validators |
| `SettingException` | the one error, naming the setting |

### Resolution order

`Placeholders.resolve(text)` (and `lookup(key)`) reads, highest first:

1. an explicit `-D` system property;
2. the settings files (their entries are system properties, so a `-D` still wins);
3. the environment: the key as written, then its variable form (`store.main.root` is `STORE_MAIN_ROOT`);
4. the default after the colon (`${a:${b:x}}` nests); with none, `resolve` leaves the reference as written, `require` throws
   `SettingException`, `resolveOr(text, view, missing)` substitutes `missing`.

A host can pass its own `view` (a `Function<String,String>`) in place of the process: a test passes a map's `get`.

### Settings files

`SettingsFile.applyFrom("app.settings.file")` reads the files named by that system property (the name is yours): absolute paths,
separated by commas, later files beating earlier ones. A relative path is refused and a missing file is an error; nothing is discovered
from the home or working folder. Call it first thing in `main`; it returns the keys it set.

```ini
# base.ini
[global]                      ; a section without a dot is a label: keys are full names
app.data.dir=/data/app
[store.main]                  ; a section with a dot is a namespace: keys are prefixed with it
root=${app.data.dir}/main     ; this is store.main.root
```

`SettingsFile.sourceOf(key)` names the file a key came from; `isSecret(key)` and `shown(key, value)` mask passwords, secrets, tokens,
credentials and keys (`SettingsFile.MASK`) wherever you show a setting. `applyFrom(property, homeProperty)` also resolves
`${homeProperty}` (the install folder) inside values.

### Secrets

`Secrets.get(settings, key)` resolves a `${ENV}` reference; `Secrets.secret(settings, key)` prefers the `<key>-file` twin (the first line
of that file, for a mounted secret) and otherwise the resolved value. A reference with no value and no default is a `SettingException`
that names the key.

### Path anchors

`PathAnchors.anchored(value, roots)` returns an absolute `Path`: the value as given when absolute (POSIX, a Windows drive or a UNC share,
on any operating system), `${root}` or `${root}/rest` under a named root, and **throws for a bare relative path**. The overload with a
`base` folder takes a relative path under it where a program allows that. `anchorValue(key, value, home, homeSetting, problems)` is the
reader's one rule for a whole setting: URLs, schemes (`s3://`, `mem://`), words and numbers are kept; a JDBC file URL has its file part
anchored.

### Tutorial: settings from files, then paths

Save as `Ex1.java` and run `java -cp <the tools-settings classes or jar> Ex1.java` (JDK 21; the build's `tools-settings/target/classes` works).

```java
import com.ash.tools.settings.*;
import java.nio.file.*;
import java.util.*;

public class Ex1 {
  public static void main(String[] a) throws Exception {
    Path dir = Files.createTempDirectory("tools-ex");
    Files.writeString(dir.resolve("base.ini"), "[global]\napp.data.dir=/data/app\n[store.main]\nroot=${app.data.dir}/main\n");
    Files.writeString(dir.resolve("secrets.ini"), "[db.reporting]\npassword=s3cret\n");
    System.setProperty("app.settings.file", dir.resolve("base.ini") + "," + dir.resolve("secrets.ini"));

    System.out.println(SettingsFile.applyFrom("app.settings.file"));       // [app.data.dir, store.main.root, db.reporting.password]
    System.out.println(Placeholders.resolve("${store.main.root}/x"));      // /data/app/main/x
    System.out.println(Placeholders.resolve("${nothing.set:fallback}"));   // fallback
    System.out.println(Placeholders.resolve("${nothing.set}"));            // ${nothing.set}   (left as written)
    System.out.println(SettingsFile.shown("db.reporting.password", "s3cret"));   // the mask, never the value
    System.out.println(PathAnchors.anchored("${data}/views", Map.of("data", Path.of("/srv/data"))));   // /srv/data/views
    try { PathAnchors.anchored("views", Map.of("data", Path.of("/srv/data"))); }
    catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    // 'views' is a relative path: give an absolute path or anchor it at one of [${data}]; a path is never taken from the working folder
    try { Placeholders.require("${nothing.set}", null); }
    catch (RuntimeException e) { System.out.println(e.getMessage()); }
    // the setting nothing.set is not set: give it with -Dnothing.set=..., in the settings file (app.settings.file), or as the environment variable NOTHING_SET
  }
}
```

The comments are the output of a real run. Override a file entry with `java -Dstore.main.root=/elsewhere ...`: the explicit `-D` wins.

See also the tools-net section: its TLS and proxy keys are `SettingSpec`s.

## tools-net: TLS and the outbound proxy

Package `com.ash.tools.net`; depends on `tools-settings`. One mechanism for every HTTP client of a program, so every connector offers
the same trust, identity and proxy keys and fails with the same clear message.

| Class | Job |
|---|---|
| `TlsSettings` | the `tls.*` keys of one connector, read from a settings map under a prefix |
| `TlsContexts` | builds the `SSLContext`, key and trust managers and `SSLParameters` from them: `TlsMaterial` |
| `TlsHttp` | applies a `TlsMaterial` to a `java.net.http.HttpClient.Builder`; `material(settings, ...)` reads and builds in one call |
| `TlsMaterial`, `CertInfo`, `PemReader` | the result, the certificates in it (with `daysLeft`, `expired`), and the PEM parsing (PKCS#8, PKCS#1 RSA, SEC1 EC, encrypted PKCS#8) |
| `ProxySettings` | the `proxy.*` keys and where the effective proxy comes from; `apply(HttpClient.Builder)`, `send`, `describe`, `note` |
| `SiteProxy` | the site-wide default a program hands over once at start; connectors inherit it |
| `NoProxy` | the bypass list: names, suffixes, addresses, CIDR blocks, `*` |
| `TlsException`, `ProxyException` | refusals naming the setting or file and the reason |

### TLS keys

Under a prefix (normally `tls.`), all optional, `enabled` defaults to false: **trust** by `ca-file` (PEM bundle, a path or the PEM text),
`truststore` (+ `-password`, `-type`) or `trust-jvm-default` (true when neither is given; true beside them merges the JVM's authorities);
**client identity** (mutual TLS) by `cert-file` + `key-file` or `keystore` (+ `keystore-password`, `keystore-type`, `key-alias`, `key-password`);
`protocols` (default `TLSv1.3,TLSv1.2`), `cipher-suites`, `verify-hostname` (true), and `insecure-trust-all` (development only; refused unless
the environment variable your program names is `true`). A password is never a literal: use `${ENV}` or the `-file` twin of the key
(`keystore-password-file`). The authoritative list, with types and defaults, is generated by code, so a form can never drift from it:

```java
TlsSettings.settingSpecs("tls.")                        // List<SettingSpec>
TlsSettings.settingSpecs("tls.", "MYAPP_ALLOW_INSECURE_TLS")   // naming your own opt-in variable
TlsContexts.build(settings, env, clock, "MYAPP_ALLOW_INSECURE_TLS")
```

`TlsContexts.build` also checks that a private key belongs to its certificate, and warns (log) when a certificate expires within
`TlsMaterial.WARN_DAYS`; `TlsMaterial.expiryNote` and `soonestExpiry` give the same for a health page.

### Proxy keys and sources

`proxy.url` (`http://host:port`: plain `http://` targets are forwarded, `https://` targets tunnelled with `CONNECT`, so TLS is still
negotiated with the target), `proxy.username` + `proxy.password` (Basic; the password only as `${ENV}` or `password-file`), `proxy.no-proxy`,
`proxy.enabled`; a bare `proxy: none` switches it off for one connector. Highest first, the effective proxy is the connector's own, the
`SiteProxy` default, then the `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY` environment variables when the site asks for them, then none.
`ProxySettings.source()` says which; `describe()`, `note()` and `toString()` never contain the password.

### Tutorial 1: the proxy

```java
import com.ash.tools.net.*;
import com.ash.tools.testkit.FakeProxy;      // from tools-testkit
import java.util.Map;

public class ProxyEx {
  public static void main(String[] a) throws Exception {
    try (FakeProxy proxy = FakeProxy.start()) {
      Map<String,String> s = Map.of("proxy.url", proxy.url(), "proxy.no-proxy", "localhost,*.internal");
      ProxySettings p = ProxySettings.resolve(s, "demo");
      System.out.println(p + " | example.com: " + p.appliesTo("example.com") + ", a.internal: " + p.appliesTo("a.internal"));
      // ProxySettings[connector, 127.0.0.1:41105] | example.com: true, a.internal: false
      System.out.println(p.describe());
      // {enabled=true, source=connector, sourceLabel=connector, url=http://127.0.0.1:41105, username=null, password=null, noProxy=[localhost, *.internal]}
      // a client that uses it:  java.net.http.HttpClient c = p.apply(java.net.http.HttpClient.newBuilder()).build();
    }
  }
}
```

### Tutorial 2: mutual TLS against a local server

`TlsFixture` (tools-testkit) writes a throw-away CA, server and client certificate; three of its settings maps are a client that trusts and
presents a certificate, one that only trusts, and one that trusts the wrong authority.

```java
import com.ash.tools.net.*;
import com.ash.tools.testkit.TlsFixture;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.util.*;

public class TlsEx {
  public static void main(String[] a) throws Exception {
    TlsFixture pki = new TlsFixture();
    HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(pki.serverContext(false)));
    server.createContext("/", x -> { byte[] b = "hello".getBytes(); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close(); });
    server.start();
    URI uri = URI.create("https://127.0.0.1:" + server.getAddress().getPort() + "/");
    try {
      for (var settings : List.of(pki.clientSettings(), pki.trustOnlySettings(), pki.wrongCaSettings())) {
        Map<String,String> s = new HashMap<>(settings);
        s.put("tls.enabled", "true");
        HttpClient client = TlsHttp.apply(HttpClient.newBuilder(), TlsContexts.build(TlsSettings.from(s))).build();
        try { System.out.println(client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body()); }
        catch (Exception e) { System.out.println("refused: " + e.getClass().getSimpleName()); }
      }
    } finally { server.stop(0); pki.delete(); }
  }
}
```

Output of a real run: `hello`, `hello`, `refused: SSLHandshakeException`.

The modules this tutorial uses are `tools-net` and `tools-testkit`; setting names and secrets are in the tools-settings section.

## tools-concurrent: SingleFlight

Package `com.ash.tools.concurrent`; JDK only. One class.

`SingleFlight<K>` loads a missing value **once at a time per key** for a loader that blocks (a file read, a network call, a query).
`ConcurrentHashMap.computeIfAbsent` and Caffeine's `get(key, loader)` run the loader inside a `synchronized` bin, and on Java 21 a
virtual thread that blocks there pins its carrier thread. `SingleFlight` waits on a `ReentrantLock`, which parks a virtual thread properly,
and the value is stored in the caller's own cache before the lock is released.

```java
V get(K key, Function<K,V> peek, Function<K,V> load, BiConsumer<K,V> store)
```

`peek` reads your cache; `load` runs for one caller only while the others wait, then they find the value with `peek`; a non-null loaded
value goes to `store`.

### Tutorial: eight callers, one load

```java
import com.ash.tools.concurrent.SingleFlight;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Ex {
  static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
  public static void main(String[] a) throws Exception {
    SingleFlight<String> flight = new SingleFlight<>();
    ConcurrentHashMap<String,String> cache = new ConcurrentHashMap<>();
    AtomicInteger loads = new AtomicInteger();
    var pool = Executors.newFixedThreadPool(8);
    var results = new ArrayList<Future<String>>();
    for (int i = 0; i < 8; i++)
      results.add(pool.submit(() -> flight.get("k", cache::get,
          k -> { loads.incrementAndGet(); sleep(100); return "v"; },   // the slow load
          cache::put)));
    for (var f : results) f.get();
    pool.shutdown();
    System.out.println("loader ran " + loads.get() + " time(s) for 8 callers");   // loader ran 1 time(s) for 8 callers
  }
}
```

(The loader is a `Function`: it cannot throw checked exceptions, so wrap them.)

## tools-time: LoadGuard

Package `com.ash.tools.time`; JDK only. One class: what every loader checks before it writes or drops a business day, so that one
mis-dated row cannot wipe a store's history.

| Rule | Behaviour |
|---|---|
| No business date in the future | a row dated after *today plus `--future-days`* (default 1) in the business zone is quarantined: not written, the first few named on standard error; `finish()` ends the load with an error once the good rows are in |
| Retention counts back from the load | a cut-off (`keepFromDays`, `keepFromMonths`, `keepFromNewest`) starts from the newest business date the load **accepted**, never the newest date the store holds; a run that writes no row counts from `--as-of`, else today |
| No mass drop by accident | `checkDrop(table, drop, total, what)` refuses a step that would drop more than `--max-drop-share` (0.5) of a table unless `--force-drop`; nothing has been dropped when it refuses |

Thread-safe: parsing threads call `accept` at once.

### Making one

`LoadGuard.fromArgs(args, zone[, clock])` reads the loader's own command line and ignores the rest: `--zone`, `--as-of yyyy-MM-dd`,
`--future-days N`, `--max-drop-share F`, `--force-drop`. The business zone is `--zone`, else the `zone` you pass, else
`LoadGuard.DEFAULT_ZONE` (`America/New_York`). `fromArgs(args, envPrefix)` reads it from the environment variable `<envPrefix>BUSINESS_ZONE`.
The constructor `new LoadGuard(zone, clock, asOf, futureDays, maxDropShare, forceDrop)` is the same thing without a command line.

### Tutorial: a Friday load

```java
import com.ash.tools.time.LoadGuard;
import java.time.*;

public class Ex {
  public static void main(String[] a) {
    Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneOffset.UTC);
    LoadGuard g = LoadGuard.fromArgs(new String[]{"--zone", "America/New_York", "--future-days", "1"}, ZoneId.of("UTC"), clock);
    System.out.println(g.today() + " latest allowed " + g.latestAllowed());           // 2026-11-20 latest allowed 2026-11-21
    System.out.println(g.accept(LocalDate.parse("2026-11-21"), "r1"));                // true
    System.out.println(g.accept(LocalDate.parse("2026-11-25"), "r2"));                // false (quarantined, named on stderr)
    System.out.println("asOf " + g.asOf() + ", keep 30 days from " + g.keepFromDays(30));   // asOf 2026-11-21, keep 30 days from 2026-10-23
    try { g.checkDrop("trade", 80, 100, "age"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    // trade: retention would drop 80 of 100 age (more than --max-drop-share 0.50), counting back from 2026-11-21; nothing was dropped. ...
    try { g.finish(); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    // 1 rows not loaded: their business date is after 2026-11-21 (today 2026-11-20 in America/New_York, plus --future-days 1); ...
  }
}
```

The comments are the output of a real run. Run it with `java -cp <tools-time classes> Ex.java`.

## tools-xlsx: the one .xlsx writer

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

### Tutorial: a priced sheet with a total and a chart

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
[BUILD_AND_IDE.md](BUILD_AND_IDE.md)); `Formulas.scan(bytes)` lists every disallowed formula of any finished file, for a release check.

### Behaviour fixed once for every writer

| Behaviour | Rule |
|---|---|
| A sheet name in a chart's references | quoted and XML-escaped |
| Sheet names | made valid and unique by the writer |
| Formulas | every formula checked against the shared function set |
| A formula whose result is NaN | cached as `#NUM!` |
| A text formula with no cached text | no cached value (a reader recalculates) |
| A NaN number | a styled empty cell when styled, else nothing |
| Number text | `Cells.num`: whole numbers without a fraction, else the shortest round-trip text |
| Styles | one standard look, named styles; a program passes its own `Styles` for a house look |
| The selected tab | the first sheet |
| Default row height | 15 (Excel's) |
| Zip entry times | one fixed time: byte-identical output for the same input |
| Compression | the fastest level (streaming large books) |
| Branding footer | the host builds it into `PrintSetup`; the writer holds no product data |
| Document properties | `Workbook.properties(core, app)` |
| `{{KEY}}` filling and comment dropping | `WorkbookTemplate.fill` |

The tests write every feature and read it back with Apache POI cell by cell (POI's formula results equal the cached ones), check the
layout (panes, widths, merges, drop-downs, conditional formats, print set-up, names, outline, tab colour), the identical bytes of two
runs, charts with their series, a template's cells and live formulas, a million cells under a 64 MB heap, and on request LibreOffice
converting the books.

## tools-codec: hashes and canonical JSON

Package `com.ash.tools.codec`; JDK only (Jackson is the test oracle, never a dependency).

| Class | Job |
|---|---|
| `Hashes` | message digests without the checked exception and the hex boiler-plate: text is hashed as UTF-8, files are read as a stream of any size, hex is lower case; any JDK algorithm, short forms for `SHA_256` and `SHA_384` |
| `CanonicalJson` | plain data as JSON text with every map's entries in key order and no spaces: the same bytes for the same value whatever order the maps were filled in, and the same text Jackson writes with `ORDER_MAP_ENTRIES_BY_KEYS`, so an identity or hash made either way is equal |

`CanonicalJson` takes `null`, `Boolean`, `CharSequence`, `Character`, enums (their name), the JDK's numbers, `Map`, `Iterable` and `Object[]`;
anything else is an `IllegalArgumentException` (convert it to plain data first). `Float` and `Double` are written as Java prints them,
non-finite ones as quoted text, as Jackson does.

### Tutorial: an identity that does not depend on map order

```java
import com.ash.tools.codec.*;
import java.util.*;

public class CodecEx {
  public static void main(String[] a) {
    Map<String,Object> one = new LinkedHashMap<>(); one.put("b", 2); one.put("a", List.of(1, "x"));
    Map<String,Object> two = new LinkedHashMap<>(); two.put("a", List.of(1, "x")); two.put("b", 2);
    System.out.println(CanonicalJson.write(one));                              // {"a":[1,"x"],"b":2}
    System.out.println(CanonicalJson.write(one).equals(CanonicalJson.write(two)));   // true
    System.out.println(Hashes.sha256Hex(CanonicalJson.write(one)));            // the same hash for both
    System.out.println(Hashes.hex(Hashes.SHA_384, "abc").length());            // 96
  }
}
```


## tools-csv: RFC 4180 reader and writer

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

### Tutorial: write, then read back

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


## tools-files: TempFolder

Package `com.ash.tools.files`; JDK only. One class.

`TempFolder` is one owner's temporary folder: `<base>/<prefix><pid>` (or `<prefix><pid>-<n>` when one process holds several), made the first
time something asks for it, marked with a small text file so a person (or `staleFolders()`) can tell it is yours, and removed when the JVM
stops cleanly or `cleanUp()` is called. **The owner chooses the base and the names**: nothing here reads a setting or changes
`java.io.tmpdir`. Folders left by dead processes are only *listed*, never removed (a reused process id or a link inside a folder could
lead a delete to files that are not yours).

| Method | Does |
|---|---|
| `TempFolder.of(base, prefix, marker)` | the process's folder; `of(base, prefix, n, marker)` one of several |
| `root()`, `made()`, `base()` | the folder (made on first call), whether it exists yet, the base |
| `createFile(prefix, suffix)`, `createDir(prefix)`, `subDir(name)` | a new file, folder or named subfolder inside it |
| `usage()` | `[bytes, files]` held now |
| `staleFolders()` | marked folders under the base whose process is gone, for a person to remove |
| `cleanUp()` | remove the folder now |
| `TempFolder.createFile(dir, ...)`, `createDir(parent, ...)`, `deleteTree(path)` | the same for a folder you already have; a temporary file in the target's own folder makes a move over the target atomic |

### Tutorial

```java
import com.ash.tools.files.TempFolder;
import java.nio.file.*;

public class FilesEx {
  public static void main(String[] a) throws Exception {
    Path base = Files.createTempDirectory("tools-files-ex");        // your own base: a setting in a real program
    TempFolder tmp = TempFolder.of(base, "myapp-", "myapp-temp-folder.txt");
    System.out.println("made yet: " + tmp.made());                  // made yet: false
    Path f = tmp.createFile("spill-", ".bin");
    System.out.println(f.getParent().getFileName().toString().startsWith("myapp-") + " " + tmp.made());   // true true
    System.out.println(tmp.usage()[1] + " files");                  // the marker and the file are counted by usage()
    tmp.cleanUp();
    System.out.println("made yet: " + tmp.made());                  // made yet: false
    TempFolder.deleteTree(base);
  }
}
```


## tools-testkit: test fixtures

Package `com.ash.tools.testkit`; JDK only, for **test** scope. Fixtures for testing code that uses tools-net without a real
network or a real certificate authority.

| Class | What it gives |
|---|---|
| `FakeProxy` | a forward proxy on a local port: forwards plain `http://` requests, tunnels `CONNECT`, counts both, can demand Basic `Proxy-Authorization` (407 otherwise) or refuse every `CONNECT` (403) |
| `TestPki` | a throw-away PKI in memory: `ca(name)`, `issue(ca, subject, ...)`; writes PEM certificates, keys in PKCS#8, PKCS#1 RSA, SEC1 EC or encrypted PKCS#8, keystores and truststores |
| `TlsFixture` | a ready PKI on disk: a CA, a server certificate for `localhost` and `127.0.0.1`, a client certificate and a second unrelated CA; `clientSettings()`, `trustOnlySettings()`, `wrongCaSettings()` are `tls.*` settings maps; `serverContext(needClientAuth)`; `delete()` removes the files |

### Tutorial: a proxy that demands credentials

```java
import com.ash.tools.testkit.FakeProxy;

try (FakeProxy proxy = FakeProxy.start().requireAuth("user", "secret")) {
    // point the code under test at proxy.url()   (http://127.0.0.1:<port>)
    // then assert what it did:
    //   proxy.forwarded() + proxy.connects()  = requests served with the right credentials
    //   proxy.rejected()                      = requests answered 407
    //   proxy.requests(), proxy.authorizations() = what it saw, in order
}
```

A complete use, with a TLS server, is in the tools-net section, tutorial 2. In your own build add the module in test scope (the BOM gives the
version):

```xml
<dependency><groupId>com.ash.tools</groupId><artifactId>tools-testkit</artifactId><scope>test</scope></dependency>
```
