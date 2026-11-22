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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip archives, written the same way every time and read safely (JDK only).
 *
 * <p>Writing: {@link #write(OutputStream, Map)} sorts the entries by name and gives every entry the same fixed timestamp, so the
 * same content always gives the same bytes. {@link #writer(OutputStream)} is the streaming form (entries in the order added, the
 * same fixed timestamp, duplicate names refused), for content too large to hold.
 *
 * <p>Reading: every entry name is checked ({@link #safeName}): an absolute path, a drive letter, a backslash or a {@code ..}
 * segment is refused ({@code zip-slip}). {@link #extract} also caps the bytes it writes.
 */
public final class Zips {

    /** The timestamp of every entry written here: 1980-01-01 00:00:00 UTC (the zip format's earliest). */
    public static final long FIXED_TIME_MILLIS = 315532800000L;

    private static final byte[] NONE = new byte[0];

    private Zips() {}

    /** Writes {@code entries} sorted by name, each with the fixed timestamp. The stream is finished but not closed. */
    public static void write(OutputStream out, Map<String, byte[]> entries) throws IOException {
        try (Writer w = writer(out)) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(entries).entrySet()) {
                w.add(e.getKey(), e.getValue());
            }
        }
    }

    /** {@link #write(OutputStream, Map)} into a byte array. */
    public static byte[] toBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        write(b, entries);
        return b.toByteArray();
    }

    /** A streaming writer over {@code out}; closing it finishes the archive but leaves {@code out} open. */
    public static Writer writer(OutputStream out) {
        return new Writer(out);
    }

    /** One archive being written. Entries are written in the order added, with the fixed timestamp and no extra fields. */
    public static final class Writer implements AutoCloseable {
        private final ZipOutputStream zip;
        private final Set<String> names = new HashSet<>();

        Writer(OutputStream out) {
            this.zip = new ZipOutputStream(new NonClosing(out));
        }

        /** Adds an entry of {@code content}, stored with a known size and checksum. */
        public Writer add(String name, byte[] content) throws IOException {
            byte[] c = content == null ? NONE : content;
            ZipEntry e = entry(name);
            e.setMethod(ZipEntry.STORED);
            e.setSize(c.length);
            e.setCompressedSize(c.length);
            CRC32 crc = new CRC32();
            crc.update(c);
            e.setCrc(crc.getValue());
            zip.putNextEntry(e);
            zip.write(c);
            zip.closeEntry();
            return this;
        }

        /** Adds an entry compressed from {@code in}, which is read to its end and not closed. */
        public Writer add(String name, InputStream in) throws IOException {
            zip.putNextEntry(entry(name));
            in.transferTo(zip);
            zip.closeEntry();
            return this;
        }

        /** Adds an entry compressed from the file {@code f}. */
        public Writer add(String name, Path f) throws IOException {
            try (InputStream in = Files.newInputStream(f)) {
                return add(name, in);
            }
        }

        private ZipEntry entry(String name) {
            String n = safeName(name);
            if (!names.add(n)) {
                throw new IllegalArgumentException("duplicate zip entry: " + n);
            }
            ZipEntry e = new ZipEntry(n);
            e.setTime(FIXED_TIME_MILLIS);
            return e;
        }

        @Override
        public void close() throws IOException {
            zip.finish();
            zip.flush();
        }
    }

    /** Every entry of the archive in {@code in}, by name, in archive order; each name is checked and each entry capped at {@code maxEntryBytes}. */
    public static Map<String, byte[]> read(InputStream in, long maxEntryBytes) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                String n = safeName(e.getName());
                if (e.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                copyCapped(zin, b, maxEntryBytes, n);
                out.put(n, b.toByteArray());
            }
        }
        return out;
    }

    /**
     * Unpacks the archive in {@code in} under {@code dir} (made if absent) and returns the files written, by entry name. Every name
     * is checked, the target must stay under {@code dir}, and no more than {@code maxTotalBytes} are written in all.
     */
    public static Map<String, Path> extract(InputStream in, Path dir, long maxTotalBytes) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Map<String, Path> out = new LinkedHashMap<>();
        long[] left = {maxTotalBytes};
        try (ZipInputStream zin = new ZipInputStream(in)) {
            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                String n = safeName(e.getName());
                Path target = root.resolve(n).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("zip entry leaves the folder: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), ".zip-", ".part");
                try {
                    try (OutputStream o = Files.newOutputStream(tmp)) {
                        left[0] -= copyCapped(zin, o, left[0], n);
                    }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
                out.put(n, target);
            }
        }
        return out;
    }

    /** {@link #extract(InputStream, Path, long)} for a file. */
    public static Map<String, Path> extract(Path zip, Path dir, long maxTotalBytes) throws IOException {
        try (InputStream in = Files.newInputStream(zip)) {
            return extract(in, dir, maxTotalBytes);
        }
    }

    /**
     * The entry name as a relative, forward-slash path; throws {@link IllegalArgumentException} for an empty name, an absolute path,
     * a drive letter, a backslash, a NUL or a {@code .} or {@code ..} segment.
     */
    public static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("empty zip entry name");
        }
        if (name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0)))) {
            throw new IllegalArgumentException("unsafe zip entry name: " + name);
        }
        for (String seg : name.split("/", -1)) {
            if (seg.equals("..") || seg.equals(".")) {
                throw new IllegalArgumentException("unsafe zip entry name: " + name);
            }
        }
        return name;
    }

    private static long copyCapped(InputStream in, OutputStream out, long max, String name) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long n = 0;
        for (int r; (r = in.read(buf)) > 0; ) {
            n += r;
            if (n > max) {
                throw new IOException("zip entry " + name + " is larger than the limit of " + max + " bytes");
            }
            out.write(buf, 0, r);
        }
        return n;
    }

    private static final class NonClosing extends java.io.FilterOutputStream {
        NonClosing(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
