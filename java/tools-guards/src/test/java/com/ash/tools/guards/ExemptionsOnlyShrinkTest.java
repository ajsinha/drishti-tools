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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The guards' exemptions ({@code exemptions.txt}: one line per exempt file, {@code <guard> <path> <reason>}) only shrink: never more
 * than {@link #CEILING}, each naming a guard that reads the list and a file that exists. The ceiling is lowered when a line goes,
 * never raised. And the domain words list ({@code domain-words.txt}) only grows: never fewer than {@link #DOMAIN_WORDS_FLOOR}.
 */
class ExemptionsOnlyShrinkTest {

    /** The exemptions at 1.0.0: none. */
    static final int CEILING = 0;
    /** The domain words at 1.0.0. */
    static final int DOMAIN_WORDS_FLOOR = 38;

    private static final Set<String> GUARDS = Set.of("DomainWordsTest", "NoProductNameTest");

    @Test
    void theExemptionsOnlyShrinkAndEachIsReal() {
        List<String> lines = Sources.resource("exemptions.txt");
        assertTrue(lines.size() <= CEILING, "exemptions only shrink: " + lines.size() + " lines, at most " + CEILING);
        List<String> wrong = new ArrayList<>();
        for (String l : lines) {
            String[] w = l.split("\\s+", 3);
            if (w.length < 3 || !GUARDS.contains(w[0]) || !Files.isRegularFile(Sources.REPO.resolve(w[1]))) {
                wrong.add(l);
            }
        }
        assertEquals(List.of(), wrong, "an exemption names no guard, no file, or gives no reason");
    }

    @Test
    void theDomainWordsOnlyGrow() {
        List<String> words = Sources.resource("domain-words.txt");
        assertTrue(words.size() >= DOMAIN_WORDS_FLOOR, "the domain words only grow: " + words.size() + ", at least " + DOMAIN_WORDS_FLOOR);
        assertEquals(words.size(), words.stream().distinct().count(), "a domain word listed twice");
    }
}
