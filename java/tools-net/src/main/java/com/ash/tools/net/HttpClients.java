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

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The one place an {@link HttpClient} starts: the proxy ({@link ProxySettings}) and the TLS material ({@link TlsMaterial}, from
 * {@link TlsHttp}) applied, a connect timeout set, nothing global touched. Every call builds a new builder; the factory holds no state.
 *
 * <pre>{@code
 * HttpClient c = HttpClients.builder(ProxySettings.resolve(settings, url), TlsHttp.material(settings, url, "url")).build();
 * }</pre>
 */
public final class HttpClients {

    /** The connect timeout when none is given. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private HttpClients() {}

    /** A builder with the proxy and TLS material applied (either may be null for none) and the default connect timeout. */
    public static HttpClient.Builder builder(ProxySettings proxy, TlsMaterial tls) {
        return builder(proxy, tls, DEFAULT_CONNECT_TIMEOUT);
    }

    /** As {@link #builder(ProxySettings, TlsMaterial)} with the given connect timeout (null leaves the JDK's: none). */
    public static HttpClient.Builder builder(ProxySettings proxy, TlsMaterial tls, Duration connectTimeout) {
        HttpClient.Builder b = HttpClient.newBuilder();
        if (connectTimeout != null) {
            if (connectTimeout.isNegative() || connectTimeout.isZero()) {
                throw new IllegalArgumentException("connect timeout must be positive: " + connectTimeout);
            }
            b.connectTimeout(connectTimeout);
        }
        if (proxy != null) {
            proxy.apply(b);
        }
        if (tls != null) {
            TlsHttp.apply(b, tls);
        }
        return b;
    }

    /** A builder for one target: the proxy decided for {@code url} from {@code settings} and the TLS material for it (null for plain HTTP). */
    public static HttpClient.Builder forTarget(java.util.Map<String, String> settings, String url, String urlSetting) {
        return builder(ProxySettings.resolve(settings, url), TlsHttp.material(settings, url, urlSetting));
    }
}
