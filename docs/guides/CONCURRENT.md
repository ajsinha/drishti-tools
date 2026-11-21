<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-concurrent: SingleFlight

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

## Tutorial: eight callers, one load

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

(The loader is a `Function`: it cannot throw checked exceptions, so wrap them.) See [BUILD_AND_IDE.md](../BUILD_AND_IDE.md) to build the jar.
