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

import java.util.Map;

/**
 * The site-wide outbound proxy (the host's site setting, handed over through {@link #configure}) that every HTTP-based connector, AI
 * provider and MCP client inherits unless it sets its own {@code proxy:} or {@code proxy: none}. The server hands it over once at
 * start (and again if the configuration is refreshed); {@link ProxySettings#resolve} reads it. The keys are the connector's,
 * under {@code proxy.} (url, username, password, no-proxy, enabled).
 *
 * <p>It is process state because the plugin SPI is Spring-free and a plugin starts with only its own settings: the one place
 * the site default can live without every plugin learning about the server's configuration.
 */
public final class SiteProxy {

    /** How a message names the site's proxy settings when the host has not named them ({@link #configure(Map, boolean, String)}). */
    public static final String DEFAULT_PREFIX = "site proxy.";

    private record State(Map<String, String> settings, boolean fromEnvironment, String prefix) {}

    private static volatile State state = new State(Map.of(), false, DEFAULT_PREFIX);

    private SiteProxy() {}

    /**
     * Sets the site default.
     *
     * @param settings the {@code proxy.*} keys (empty for none); an empty {@code proxy.url} means no site proxy
     * @param fromEnvironment honour {@code HTTPS_PROXY}, {@code HTTP_PROXY} and {@code NO_PROXY} when no proxy is configured
     */
    public static void configure(Map<String, String> settings, boolean fromEnvironment) {
        configure(settings, fromEnvironment, DEFAULT_PREFIX);
    }

    /**
     * As {@link #configure(Map, boolean)}; {@code prefix} is how messages name the site's settings ({@code app.http.proxy.}), so an
     * error points the operator at the host's own key.
     */
    public static void configure(Map<String, String> settings, boolean fromEnvironment, String prefix) {
        state = new State(Map.copyOf(settings), fromEnvironment, prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix);
        ProxySettings.allowBasicThroughTunnels();
    }

    /** Back to no site default (tests). */
    public static void reset() {
        state = new State(Map.of(), false, DEFAULT_PREFIX);
    }

    static Map<String, String> settings() {
        return state.settings();
    }

    static String prefix() {
        return state.prefix();
    }

    static boolean fromEnvironment() {
        return state.fromEnvironment();
    }
}
