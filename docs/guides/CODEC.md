<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-codec: hashes and canonical JSON

Package `com.ash.tools.codec`; JDK only (Jackson is the test oracle, never a dependency).

| Class | Job |
|---|---|
| `Hashes` | message digests without the checked exception and the hex boiler-plate: text is hashed as UTF-8, files are read as a stream of any size, hex is lower case; any JDK algorithm, short forms for `SHA_256` and `SHA_384` |
| `CanonicalJson` | plain data as JSON text with every map's entries in key order and no spaces: the same bytes for the same value whatever order the maps were filled in, and the same text Jackson writes with `ORDER_MAP_ENTRIES_BY_KEYS`, so an identity or hash made either way is equal |

`CanonicalJson` takes `null`, `Boolean`, `CharSequence`, `Character`, enums (their name), the JDK's numbers, `Map`, `Iterable` and `Object[]`;
anything else is an `IllegalArgumentException` (convert it to plain data first). `Float` and `Double` are written as Java prints them,
non-finite ones as quoted text, as Jackson does.

## Tutorial: an identity that does not depend on map order

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

Build and class path: [BUILD_AND_IDE.md](../BUILD_AND_IDE.md).
