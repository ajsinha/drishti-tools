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
package com.ash.tools.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * No main source holds a word of a business domain ({@code domain-words.txt}): no identifier, string or comment, as a whole word.
 * Identifiers are split at their humps ({@code tradeId} is {@code trade} and {@code id}; the whole token is a word as well). A word written with capitals in the list
 * ({@code VaR}) is matched as written, the others in any case. The list holds unambiguous words only and only grows.
 */
class DomainWordsTest {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]+");
    private static final Pattern HUMP = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    /** The words of a text: letters only, identifiers split at their humps. */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String[] parts = HUMP.split(m.group());
            if (parts.length > 1) {
                out.add(m.group());                                 // the whole token too: a word written with capitals (VaR) has humps
            }
            for (String w : parts) {
                out.add(w);
            }
        }
        return out;
    }

    /** The listed words a text holds ({@code exact}: matched as written; {@code anyCase}: lower case). */
    static Set<String> hits(String text, Set<String> exact, Set<String> anyCase) {
        Set<String> out = new TreeSet<>();
        for (String w : words(text)) {
            if (exact.contains(w)) {
                out.add(w);
            } else if (anyCase.contains(w.toLowerCase(Locale.ROOT))) {
                out.add(w.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    @Test
    void noMainSourceHoldsADomainWord() {
        Set<String> exact = new TreeSet<>();
        Set<String> anyCase = new TreeSet<>();
        for (String w : Sources.resource("domain-words.txt")) {
            (w.equals(w.toLowerCase(Locale.ROOT)) ? anyCase : exact).add(w);
        }
        Set<String> exempt = Sources.exemptions("DomainWordsTest");
        List<String> found = new ArrayList<>();
        for (Path p : Sources.mainSources()) {
            Set<String> h = hits(Sources.read(p), exact, anyCase);
            if (!h.isEmpty() && !exempt.contains(Sources.rel(p))) {
                found.add(Sources.rel(p) + " " + h);
            }
        }
        assertEquals(List.of(), found, "domain words in main sources (a tool is domain-free: name it for what it does)");
    }

    @Test
    void theCheckSplitsIdentifiersAndKeepsTheCaseOfAWordWrittenWithCapitals() {
        Set<String> exact = Set.of("VaR");
        Set<String> anyCase = Set.of("trade", "desk");
        assertEquals(Set.of("trade", "desk", "VaR"), hits("int tradeId; // the DESK's VaR", exact, anyCase));
        assertEquals(Set.of(), hits("var x = trader; String deskew;", exact, anyCase));
    }
}
