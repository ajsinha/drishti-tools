<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-files: TempFolder

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

## Tutorial

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

Build and class path: [BUILD_AND_IDE.md](../BUILD_AND_IDE.md).
