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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Message digests without the checked exception and the hex boiler-plate: text is hashed as UTF-8, files are read as a stream (any
 * size), hex is lower case. Any algorithm the JDK provides; {@link #SHA_256} and {@link #SHA_384} have short forms. JDK only.
 */
public final class Hashes {

    /** {@code SHA-256}. */
    public static final String SHA_256 = "SHA-256";
    /** {@code SHA-384}. */
    public static final String SHA_384 = "SHA-384";

    private static final int BUFFER = 1 << 16;

    private Hashes() {}

    /** A new digest of {@code algorithm}; an algorithm the JDK lacks is an {@link IllegalArgumentException}. */
    public static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("no message digest " + algorithm + " in this JDK", e);
        }
    }

    /** A new SHA-256 digest. */
    public static MessageDigest sha256() {
        return digest(SHA_256);
    }

    /** The digest of {@code bytes}. */
    public static byte[] of(String algorithm, byte[] bytes) {
        return digest(algorithm).digest(bytes);
    }

    /** The digest of {@code text} as UTF-8. */
    public static byte[] of(String algorithm, String text) {
        return of(algorithm, text.getBytes(StandardCharsets.UTF_8));
    }

    /** The digest of a file, read as a stream. */
    public static byte[] of(String algorithm, Path file) throws IOException {
        MessageDigest md = digest(algorithm);
        try (InputStream in = Files.newInputStream(file)) {
            update(md, in);
        }
        return md.digest();
    }

    /** Feeds everything {@code in} holds to {@code md} (the stream is the caller's: not closed). */
    public static MessageDigest update(MessageDigest md, InputStream in) throws IOException {
        byte[] buf = new byte[BUFFER];
        int n;
        while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
        }
        return md;
    }

    /** Feeds {@code text} as UTF-8 to {@code md}. */
    public static MessageDigest update(MessageDigest md, String text) {
        md.update(text.getBytes(StandardCharsets.UTF_8));
        return md;
    }

    /** Lower-case hex of {@code bytes}. */
    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /** Lower-case hex of what {@code md} has been fed (the digest is reset, as {@link MessageDigest#digest()} does). */
    public static String hex(MessageDigest md) {
        return hex(md.digest());
    }

    /** Lower-case hex digest of {@code bytes}. */
    public static String hex(String algorithm, byte[] bytes) {
        return hex(of(algorithm, bytes));
    }

    /** Lower-case hex digest of {@code text} as UTF-8. */
    public static String hex(String algorithm, String text) {
        return hex(of(algorithm, text));
    }

    /** Lower-case hex digest of a file, read as a stream. */
    public static String hex(String algorithm, Path file) throws IOException {
        return hex(of(algorithm, file));
    }

    /** Lower-case hex SHA-256 of {@code text} as UTF-8. */
    public static String sha256Hex(String text) {
        return hex(SHA_256, text);
    }

    /** Lower-case hex SHA-256 of {@code bytes}. */
    public static String sha256Hex(byte[] bytes) {
        return hex(SHA_256, bytes);
    }

    /** Lower-case hex SHA-256 of a file, read as a stream. */
    public static String sha256Hex(Path file) throws IOException {
        return hex(SHA_256, file);
    }
}
