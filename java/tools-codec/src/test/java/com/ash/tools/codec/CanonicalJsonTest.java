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
package com.ash.tools.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The oracle: the text Jackson writes with ORDER_MAP_ENTRIES_BY_KEYS (the way Drishti made its canonical text so far). */
class CanonicalJsonTest {

    private static final ObjectMapper JACKSON = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static void same(Object v) throws Exception {
        assertThat(CanonicalJson.write(v)).isEqualTo(JACKSON.writeValueAsString(v));
        assertThat(CanonicalJson.bytes(v)).isEqualTo(JACKSON.writeValueAsString(v).getBytes(StandardCharsets.UTF_8));
    }

    enum Side { BUY, SELL }

    @Test
    void scalarsAsJacksonWritesThem() throws Exception {
        for (Object v : new Object[] {null, true, false, 0, -7, Long.MAX_VALUE, (short) 3, (byte) -2, 1.0, -0.0, 1e10, 1.5e-7, 123456789.125,
                Double.NaN, Double.POSITIVE_INFINITY, 1.25f, Float.NaN, new BigDecimal("1.50"), new BigDecimal("1E+3"),
                new BigInteger("123456789012345678901234567890"), "", "plain", 'c', Side.SELL}) {
            same(v);
        }
    }

    @Test
    void stringsEscapedAsJacksonDoes() throws Exception {
        StringBuilder all = new StringBuilder();
        for (char c = 0; c < 0x80; c++) {
            all.append(c);
        }
        same(all.toString());
        same("quote \" backslash \\ slash / é €   😀 \u007F");
    }

    @Test
    void mapsInKeyOrderWhateverTheFillOrder() throws Exception {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", 1);
        a.put("a", List.of(Map.of("z", 1, "y", 2), "x"));
        a.put("B", null);
        a.put("aa", Map.of());
        Map<String, Object> b = new TreeMap<>(java.util.Comparator.reverseOrder());
        b.putAll(a);
        same(a);
        assertThat(CanonicalJson.write(a)).isEqualTo(CanonicalJson.write(b)).isEqualTo("{\"B\":null,\"a\":[{\"y\":2,\"z\":1},\"x\"],\"aa\":{},\"b\":1}");
        Object[] arr = {1, "two", null};
        assertThat(CanonicalJson.write(arr)).isEqualTo(JACKSON.writeValueAsString(arr));
    }

    @Test
    void randomPlainDataEqualsTheOracle() throws Exception {
        Random r = new Random(42);
        for (int i = 0; i < 300; i++) {
            same(random(r, 0));
        }
    }

    private static Object random(Random r, int depth) {
        int k = r.nextInt(depth > 3 ? 6 : 8);
        return switch (k) {
            case 0 -> null;
            case 1 -> r.nextBoolean();
            case 2 -> r.nextLong();
            case 3 -> r.nextDouble() * Math.pow(10, r.nextInt(40) - 20);
            case 4 -> text(r);
            case 5 -> new BigDecimal(BigInteger.valueOf(r.nextLong()), r.nextInt(10) - 3);
            case 6 -> {
                Map<String, Object> m = new HashMap<>();
                for (int j = r.nextInt(5); j > 0; j--) {
                    m.put(text(r), random(r, depth + 1));
                }
                yield m;
            }
            default -> {
                List<Object> l = new ArrayList<>();
                for (int j = r.nextInt(5); j > 0; j--) {
                    l.add(random(r, depth + 1));
                }
                yield l;
            }
        };
    }

    private static String text(Random r) {
        char[] c = new char[r.nextInt(8)];
        for (int i = 0; i < c.length; i++) {
            c[i] = (char) (r.nextInt(4) == 0 ? r.nextInt(0x40) : 0x20 + r.nextInt(0x300));
        }
        return new String(c);
    }

    @Test
    void anythingElseIsRefused() {
        assertThatThrownBy(() -> CanonicalJson.write(Map.of("x", new Object()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Object");
        assertThatThrownBy(() -> CanonicalJson.write(new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThat(CanonicalJson.write(Arrays.asList(1, null))).isEqualTo("[1,null]");
    }
}
