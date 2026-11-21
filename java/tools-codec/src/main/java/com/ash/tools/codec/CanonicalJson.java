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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Canonical JSON text of plain data: every map's entries in key order (keys as text, {@link String#compareTo}), no spaces, the
 * same bytes for the same value whatever order the maps were filled in. The text is the one Jackson writes with
 * {@code ORDER_MAP_ENTRIES_BY_KEYS} for the same value (its tests hold that), so an identity or hash made either way is equal.
 *
 * <p>Values: {@code null}, {@link Boolean}, {@link CharSequence}, {@link Character}, {@link Enum} (its name), the JDK's numbers
 * ({@code Byte} to {@code Long}, {@link BigInteger}, {@link BigDecimal} as {@code toString}; {@code Float} and {@code Double} as
 * Java prints them, non-finite ones as quoted text as Jackson does), {@link Map}, {@link Iterable} and {@code Object[]}. Anything
 * else is an {@link IllegalArgumentException}: convert it to plain data first. JDK only.
 */
public final class CanonicalJson {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private CanonicalJson() {}

    /** The canonical text of {@code value}. */
    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value);
        return b.toString();
    }

    /** The canonical text of {@code value} as UTF-8. */
    public static byte[] bytes(Object value) {
        return write(value).getBytes(StandardCharsets.UTF_8);
    }

    /** Appends the canonical text of {@code value} to {@code b}. */
    public static StringBuilder write(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
        } else if (value instanceof Boolean x) {
            b.append(x.booleanValue());
        } else if (value instanceof CharSequence || value instanceof Character) {
            quote(b, value.toString());
        } else if (value instanceof Enum<?> e) {
            quote(b, e.name());
        } else if (value instanceof Double d) {
            number(b, d.isNaN() || d.isInfinite(), d.toString());
        } else if (value instanceof Float f) {
            number(b, f.isNaN() || f.isInfinite(), f.toString());
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal) {
            b.append(value);
        } else if (value instanceof Map<?, ?> m) {
            object(b, m);
        } else if (value instanceof Iterable<?> it) {
            array(b, it);
        } else if (value instanceof Object[] a) {
            array(b, java.util.Arrays.asList(a));
        } else {
            throw new IllegalArgumentException("not plain data for canonical JSON: " + value.getClass().getName());
        }
        return b;
    }

    private static void number(StringBuilder b, boolean asText, String text) {
        if (asText) {
            quote(b, text);
        } else {
            b.append(text);
        }
    }

    private static void object(StringBuilder b, Map<?, ?> m) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(m.size());
        for (Map.Entry<?, ?> e : m.entrySet()) {
            entries.add(new java.util.AbstractMap.SimpleImmutableEntry<>(String.valueOf(e.getKey()), e.getValue()));
        }
        entries.sort(Map.Entry.comparingByKey());
        b.append('{');
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            quote(b, entries.get(i).getKey());
            b.append(':');
            write(b, entries.get(i).getValue());
        }
        b.append('}');
    }

    private static void array(StringBuilder b, Iterable<?> items) {
        b.append('[');
        boolean first = true;
        for (Object o : items) {
            if (!first) {
                b.append(',');
            }
            first = false;
            write(b, o);
        }
        b.append(']');
    }

    /** Appends {@code s} as a JSON string: {@code "} and {@code \} escaped, control characters as {@code \b \t \n \f \r} or {@code \}{@code u00XX}. */
    public static StringBuilder quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\f' -> b.append("\\f");
                case '\r' -> b.append("\\r");
                default -> {
                    if (c < 0x20) {
                        b.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xF]);
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"');
    }
}
