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
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * No main source names a product: not Drishti, not Rachana, not Lake's package or variables. A product's property names, variable
 * names and folders are parameters the product passes (that is exactly where the copies these modules came from differed).
 */
class NoProductNameTest {

    static final Pattern PRODUCT = Pattern.compile("(?i:\\bdrishti\\b|\\brachana\\b)|com\\.ash\\.lake\\b|com\\.ash\\.drishti\\b|com\\.ash\\.rachana\\b|DRISHTI_|RACHANA_|\\bLAKE_");

    @Test
    void noMainSourceNamesAProduct() {
        Set<String> exempt = Sources.exemptions("NoProductNameTest");
        List<String> found = new ArrayList<>();
        for (Path p : Sources.mainSources()) {
            var m = PRODUCT.matcher(Sources.read(p));
            if (m.find() && !exempt.contains(Sources.rel(p))) {
                found.add(Sources.rel(p) + " '" + m.group() + "'");
            }
        }
        assertEquals(List.of(), found, "a product's name in a main source: make it a parameter the host passes");
    }

    @Test
    void theCheckSeesEveryForm() {
        for (String s : List.of("Drishti", "the drishti.home", "com.ash.lake.Placeholders", "DRISHTI_HOME", "LAKE_ALLOW_INSECURE_TLS", "Rachana")) {
            assertEquals(true, PRODUCT.matcher(s).find(), s);
        }
        for (String s : List.of("a lake of data", "APP_HOME", "drishtis")) {
            assertEquals(false, PRODUCT.matcher(s).find(), s);
        }
    }
}
