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
package com.ash.tools.guards.kit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** The public API of compiled classes as sorted text lines, one per public or protected member: the form every API baseline file takes. */
public final class ApiSurface {

    private ApiSurface() {
    }

    public static boolean visible(int mod) {
        return Modifier.isPublic(mod) || Modifier.isProtected(mod);
    }

    /** The lines of every public class of package {@code pkg}, read from {@code classesRoot} (a module's target/classes). */
    public static List<String> of(Path classesRoot, String pkg, ClassLoader loader) {
        Path dir = classesRoot.resolve(pkg.replace('.', '/'));
        if (!Files.isDirectory(dir)) {
            throw new AssertionError(dir + " is missing: build the modules first");
        }
        TreeSet<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".class")).sorted().toList()) {
                String n = f.getFileName().toString();
                if (n.contains("$") || n.equals("package-info.class")) {
                    continue;                                       // nested classes come with their owner
                }
                try {
                    describe(Class.forName(pkg + "." + n.substring(0, n.length() - 6), false, loader), out);
                } catch (ClassNotFoundException e) {
                    throw new AssertionError(e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArrayList<>(out);
    }

    public static void describe(Class<?> c, Set<String> out) {
        if (!visible(c.getModifiers())) {
            return;
        }
        String owner = c.getName();
        StringBuilder head = new StringBuilder("class " + c.toGenericString());
        if (c.getGenericSuperclass() != null && c.getSuperclass() != Object.class && !c.isEnum() && !c.isRecord()) {
            head.append(" extends ").append(c.getGenericSuperclass().getTypeName());
        }
        for (java.lang.reflect.Type i : c.getGenericInterfaces()) {
            head.append(" implements ").append(i.getTypeName());
        }
        out.add(head.toString());
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (visible(k.getModifiers()) && !k.isSynthetic()) {
                out.add(owner + " :: " + k.toGenericString());
            }
        }
        for (Method m : c.getDeclaredMethods()) {
            if (visible(m.getModifiers()) && !m.isSynthetic() && !m.isBridge()) {
                out.add(owner + " :: " + m.toGenericString());
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (visible(f.getModifiers()) && !f.isSynthetic()) {
                out.add(owner + " :: " + f.toGenericString());
            }
        }
        for (Class<?> n : c.getDeclaredClasses()) {
            describe(n, out);
        }
    }
}
