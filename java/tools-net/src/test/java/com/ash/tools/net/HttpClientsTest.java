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

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The one HTTP client factory: proxy and timeouts applied, nothing shared between clients. */
class HttpClientsTest {

    @AfterEach
    void reset() {
        SiteProxy.reset();
    }

    @Test
    void aProxyIsAppliedAndTheNoProxyListGoesDirect() {
        ProxySettings p = ProxySettings.resolve(Map.of("proxy.url", "http://proxy.corp:3128", "proxy.no-proxy", "intranet.example"),
                "https://api.example.com", k -> null);
        HttpClient c = HttpClients.builder(p, null).build();
        assertThat(c.proxy()).isPresent();
        assertThat(c.proxy().get().select(URI.create("https://api.example.com/x"))).hasSize(1)
                .allSatisfy(px -> assertThat(px.address().toString()).contains("proxy.corp"));
        assertThat(c.proxy().get().select(URI.create("https://db.intranet.example/x"))).allSatisfy(px -> assertThat(px.type()).isEqualTo(java.net.Proxy.Type.DIRECT));
    }

    @Test
    void noProxyNoTlsGivesAPlainClientWithTheDefaultConnectTimeout() {
        HttpClient c = HttpClients.builder(ProxySettings.none(), null).build();
        assertThat(c.proxy()).isEmpty();
        assertThat(c.connectTimeout()).contains(HttpClients.DEFAULT_CONNECT_TIMEOUT);
        assertThat(HttpClients.builder((ProxySettings) null, null).build().proxy()).isEmpty();
    }

    @Test
    void theConnectTimeoutIsTheCallers() {
        assertThat(HttpClients.builder(null, null, Duration.ofSeconds(3)).build().connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(HttpClients.builder(null, null, null).build().connectTimeout()).isEmpty();
        assertThatThrownBy(() -> HttpClients.builder(null, null, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildersAreIndependentAndLeaveGlobalStateAlone() {
        java.net.ProxySelector before = java.net.ProxySelector.getDefault();
        ProxySettings p = ProxySettings.resolve(Map.of("proxy.url", "http://proxy.corp:3128"), "https://a.example", k -> null);
        HttpClient with = HttpClients.builder(p, null).build();
        HttpClient without = HttpClients.builder(null, null).build();
        assertThat(with).isNotSameAs(without);
        assertThat(without.proxy()).isEmpty();
        assertThat(java.net.ProxySelector.getDefault()).isSameAs(before);
    }

    @Test
    void aPlainHttpUrlWithTlsSettingsIsRefusedNotDowngraded() {
        assertThatThrownBy(() -> HttpClients.forTarget(Map.of("tls.enabled", "true"), "http://x.example", "url")).isInstanceOf(TlsException.class);
        assertThat(HttpClients.forTarget(Map.of(), "http://x.example", "url").build()).isNotNull();
    }
}
