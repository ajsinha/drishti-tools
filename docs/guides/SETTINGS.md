<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-settings: settings, placeholders, secrets and path anchors

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

## Resolution order

`Placeholders.resolve(text)` (and `lookup(key)`) reads, highest first:

1. an explicit `-D` system property;
2. the settings files (their entries are system properties, so a `-D` still wins);
3. the environment: the key as written, then its variable form (`store.main.root` is `STORE_MAIN_ROOT`);
4. the default after the colon (`${a:${b:x}}` nests); with none, `resolve` leaves the reference as written, `require` throws
   `SettingException`, `resolveOr(text, view, missing)` substitutes `missing`.

A host can pass its own `view` (a `Function<String,String>`) in place of the process: a test passes a map's `get`.

## Settings files

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

## Secrets

`Secrets.get(settings, key)` resolves a `${ENV}` reference; `Secrets.secret(settings, key)` prefers the `<key>-file` twin (the first line
of that file, for a mounted secret) and otherwise the resolved value. A reference with no value and no default is a `SettingException`
that names the key.

## Path anchors

`PathAnchors.anchored(value, roots)` returns an absolute `Path`: the value as given when absolute (POSIX, a Windows drive or a UNC share,
on any operating system), `${root}` or `${root}/rest` under a named root, and **throws for a bare relative path**. The overload with a
`base` folder takes a relative path under it where a program allows that. `anchorValue(key, value, home, homeSetting, problems)` is the
reader's one rule for a whole setting: URLs, schemes (`s3://`, `mem://`), words and numbers are kept; a JDBC file URL has its file part
anchored.

## Tutorial: settings from files, then paths

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

See also: [NET.md](NET.md) (its TLS and proxy keys are `SettingSpec`s), [BUILD_AND_IDE.md](../BUILD_AND_IDE.md).
