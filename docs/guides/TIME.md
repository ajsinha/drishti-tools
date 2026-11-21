<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-time: LoadGuard

Package `com.ash.tools.time`; JDK only. One class: what every loader checks before it writes or drops a business day, so that one
mis-dated row cannot wipe a store's history.

| Rule | Behaviour |
|---|---|
| No business date in the future | a row dated after *today plus `--future-days`* (default 1) in the business zone is quarantined: not written, the first few named on standard error; `finish()` ends the load with an error once the good rows are in |
| Retention counts back from the load | a cut-off (`keepFromDays`, `keepFromMonths`, `keepFromNewest`) starts from the newest business date the load **accepted**, never the newest date the store holds; a run that writes no row counts from `--as-of`, else today |
| No mass drop by accident | `checkDrop(table, drop, total, what)` refuses a step that would drop more than `--max-drop-share` (0.5) of a table unless `--force-drop`; nothing has been dropped when it refuses |

Thread-safe: parsing threads call `accept` at once.

## Making one

`LoadGuard.fromArgs(args, zone[, clock])` reads the loader's own command line and ignores the rest: `--zone`, `--as-of yyyy-MM-dd`,
`--future-days N`, `--max-drop-share F`, `--force-drop`. The business zone is `--zone`, else the `zone` you pass, else
`LoadGuard.DEFAULT_ZONE` (`America/New_York`). `fromArgs(args, envPrefix)` reads it from the environment variable `<envPrefix>BUSINESS_ZONE`.
The constructor `new LoadGuard(zone, clock, asOf, futureDays, maxDropShare, forceDrop)` is the same thing without a command line.

## Tutorial: a Friday load

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

The comments are the output of a real run. Run it with `java -cp <tools-time classes> Ex.java`; see [BUILD_AND_IDE.md](../BUILD_AND_IDE.md).
