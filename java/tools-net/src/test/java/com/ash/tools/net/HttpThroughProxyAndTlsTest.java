/*
 * Tools · shared, domain-free Java utilities.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.tools.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.tools.testkit.FakeProxy;
import com.ash.tools.testkit.TlsFixture;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The module and the test kit together: the JDK's HTTP client, the TLS material and the proxy, end to end on local ports. */
class HttpThroughProxyAndTlsTest {

    private final TlsFixture tls = new TlsFixture();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
        tls.delete();
    }

    private String start(boolean https) throws Exception {
        InetSocketAddress at = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        if (https) {
            HttpsServer s = HttpsServer.create(at, 0);
            s.setHttpsConfigurator(new HttpsConfigurator(tls.serverContext(false)));
            server = s;
        } else {
            server = HttpServer.create(at, 0);
        }
        server.createContext("/hello", x -> {
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            x.sendResponseHeaders(200, body.length);
            x.getResponseBody().write(body);
            x.close();
        });
        server.start();
        return (https ? "https" : "http") + "://localhost:" + server.getAddress().getPort() + "/hello";
    }

    private static HttpResponse<String> get(Map<String, String> settings, String url) throws Exception {
        HttpClient.Builder b = HttpClient.newBuilder();
        TlsMaterial m = TlsHttp.material(settings, url, "url", k -> null);
        if (m != null) {
            TlsHttp.apply(b, m);
        }
        ProxySettings p = ProxySettings.resolve(settings, url, k -> null);
        p.apply(b);
        return p.send(b.build(), HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void httpsThroughAnAuthenticatingProxyWithTheFixturesTrust() throws Exception {
        String url = start(true);
        try (FakeProxy proxy = FakeProxy.start().requireAuth("svc", "pw")) {
            Map<String, String> s = new HashMap<>(tls.trustOnlySettings());
            s.putAll(Map.of("proxy.url", proxy.url(), "proxy.username", "svc", "proxy.password", "pw"));
            HttpResponse<String> r = get(s, url);
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).isEqualTo("hello");
            assertThat(proxy.connects()).isEqualTo(1);
        }
    }

    @Test
    void plainHttpIsForwardedAndAWrongAuthorityIsRefused() throws Exception {
        String url = start(false);
        try (FakeProxy proxy = FakeProxy.start()) {
            assertThat(get(Map.of("proxy.url", proxy.url()), url).body()).isEqualTo("hello");
            assertThat(proxy.forwarded()).isEqualTo(1);
        }
        server.stop(0);
        String secure = start(true);
        assertThatThrownBy(() -> get(tls.wrongCaSettings(), secure)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> TlsHttp.material(tls.trustOnlySettings(), url, "url", k -> null)).isInstanceOf(TlsException.class)
                .hasMessageContaining("not https://");
    }
}
