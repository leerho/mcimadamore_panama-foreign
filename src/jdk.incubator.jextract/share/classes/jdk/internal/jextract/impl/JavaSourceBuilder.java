/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.jextract.impl;

import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.jextract.Type;

import javax.tools.JavaFileObject;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Superclass for .java source generator classes.
 */
abstract class JavaSourceBuilder extends StringSourceBuilder {

    enum Kind {
        CLASS("class"),
        INTERFACE("interface");

        final String kindName;

        Kind(String kindName) {
            this.kindName = kindName;
        }
    }

    static final String PUB_CLS_MODS = "public final ";
    static final String PUB_MODS = "public static ";
    final Kind kind;
    protected final String className;
    protected final String pkgName;

    Set<String> nestedClassNames = new HashSet<>();
    int nestedClassNameCount = 0;

    JavaSourceBuilder(int align, Kind kind, String className, String pkgName) {
        super(align);
        this.kind = kind;
        this.className = className;
        this.pkgName = pkgName;
    }

    JavaSourceBuilder(Kind kind, String className, String pkgName) {
        this(0, kind, className, pkgName);
    }

    String superClass() {
        return null;
    }

    protected String getClassModifiers() {
        return PUB_CLS_MODS;
    }

    void classBegin() {
        emitPackagePrefix();
        emitImportSection();

        indent();
        append(getClassModifiers());
        append(kind.kindName + " " + className);
        if (superClass() != null) {
            append(" extends ");
            append(superClass());
        }
        append(" {\n\n");
    }

    JavaSourceBuilder classEnd() {
        indent();
        append("}\n\n");
        return this;
    }

    // public API (used by OutputFactory)

    public void addVar(String javaName, String nativeName, MemoryLayout layout, Class<?> type) {
        throw new UnsupportedOperationException();
    }

    public void addFunction(String javaName, String nativeName, MethodType mtype, FunctionDescriptor desc, boolean varargs, List<String> paramNames) {
        throw new UnsupportedOperationException();
    }

    public void addConstant(String javaName, Class<?> type, Object value) {
        throw new UnsupportedOperationException();
    }

    public void addTypedef(String name, String superClass, Type type) {
        throw new UnsupportedOperationException();
    }

    public StructBuilder addStruct(String name, GroupLayout parentLayout, Type type) {
        return new StructBuilder(this, name, parentLayout, type);
    }

    public void addFunctionalInterface(String name, MethodType mtype, FunctionDescriptor desc) {
        FunctionalInterfaceBuilder builder = new FunctionalInterfaceBuilder(this, name, mtype, desc);
        builder.classBegin();
        builder.classEnd();
    }

    public List<JavaFileObject> toFiles() {
        classEnd();
        String res = build();
        return List.of(Utils.fileFromString(pkgName, className, res));
    }

    // Internal generation helpers (used by other builders)

    /*
     * We may have case-insensitive name collision! A C program may have
     * defined structs/unions/typedefs with the names FooS, fooS, FoOs, fOOs.
     * Because we map structs/unions/typedefs to nested classes of header classes,
     * such a case-insensitive name collision is problematic. This is because in
     * a case-insensitive file system javac will overwrite classes for
     * Header$CFooS, Header$CfooS, Header$CFoOs and so on! We solve this by
     * generating unique case-insensitive names for nested classes.
     */
    final String uniqueNestedClassName(String name) {
        name = Utils.javaSafeIdentifier(name);
        return nestedClassNames.add(name.toLowerCase()) ? name : (name + "$" + nestedClassNameCount++);
    }

    protected void emitPackagePrefix() {
        assert pkgName.indexOf('/') == -1 : "package name invalid: " + pkgName;
        append("// Generated by jextract\n\n");
        if (!pkgName.isEmpty()) {
            append("package ");
            append(pkgName);
            append(";\n\n");
        }
    }

    protected void emitImportSection() {
        append("import java.lang.invoke.MethodHandle;\n");
        append("import java.lang.invoke.VarHandle;\n");
        append("import java.util.Objects;\n");
        append("import jdk.incubator.foreign.*;\n");
        append("import jdk.incubator.foreign.MemoryLayout.PathElement;\n");
        append("import static ");
        append(OutputFactory.C_LANG_CONSTANTS_HOLDER);
        append(".*;\n");
    }

    protected void emitGetter(Class<?> type, String name, String access, boolean nullCheck, String errMsg) {
        incrAlign();
        indent();
        append(PUB_MODS + " " + type.getSimpleName() + " " +name + "() {\n");
        incrAlign();
        indent();
        append("return ");
        if (nullCheck) {
            append("RuntimeHelper.requireNonNull(");
        }
        append(access);
        if (nullCheck) {
            append(",\"");
            append(errMsg);
            append("\")");
        }
        append(";\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    protected void emitGetter(Class<?> type, String name, String access) {
        emitGetter(type, name, access, false, null);
    }
}
