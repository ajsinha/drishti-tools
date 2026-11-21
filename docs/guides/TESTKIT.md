<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Tools. Proprietary: see LICENSE. -->
# tools-testkit: test fixtures

Package `com.ash.tools.testkit`; JDK only, for **test** scope. Fixtures for testing code that uses [tools-net](NET.md) without a real
network or a real certificate authority.

| Class | What it gives |
|---|---|
| `FakeProxy` | a forward proxy on a local port: forwards plain `http://` requests, tunnels `CONNECT`, counts both, can demand Basic `Proxy-Authorization` (407 otherwise) or refuse every `CONNECT` (403) |
| `TestPki` | a throw-away PKI in memory: `ca(name)`, `issue(ca, subject, ...)`; writes PEM certificates, keys in PKCS#8, PKCS#1 RSA, SEC1 EC or encrypted PKCS#8, keystores and truststores |
| `TlsFixture` | a ready PKI on disk: a CA, a server certificate for `localhost` and `127.0.0.1`, a client certificate and a second unrelated CA; `clientSettings()`, `trustOnlySettings()`, `wrongCaSettings()` are `tls.*` settings maps; `serverContext(needClientAuth)`; `delete()` removes the files |

## Tutorial: a proxy that demands credentials

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

A complete use, with a TLS server, is in [NET.md](NET.md), tutorial 2. In your own build add the module in test scope (the BOM gives the
version):

```xml
<dependency><groupId>com.ash.tools</groupId><artifactId>tools-testkit</artifactId><scope>test</scope></dependency>
```
