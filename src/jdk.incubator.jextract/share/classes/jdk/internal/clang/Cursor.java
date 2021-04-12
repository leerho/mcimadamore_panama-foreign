/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.clang;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.internal.clang.libclang.Index_h;

import java.util.ArrayList;
import java.util.stream.Stream;

public final class Cursor {

    private final MemorySegment cursor;
    private final int kind;

    Cursor(MemorySegment cursor) {
        this.cursor = cursor;
        kind = Index_h.clang_getCursorKind(cursor);
    }

    public boolean isDeclaration() {
        return Index_h.clang_isDeclaration(kind) != 0;
    }

    public boolean isPreprocessing() {
        return Index_h.clang_isPreprocessing(kind) != 0;
    }

    public boolean isInvalid() {
        return Index_h.clang_isInvalid(kind) != 0;
    }

    public boolean isDefinition() {
        return Index_h.clang_isCursorDefinition(cursor) != 0;
    }

    public boolean isAttribute() { return Index_h.clang_isAttribute(kind) != 0; }

    public boolean isAnonymousStruct() {
        return Index_h.clang_Cursor_isAnonymousRecordDecl(cursor) != 0;
    }

    public boolean isMacroFunctionLike() {
        return Index_h.clang_Cursor_isMacroFunctionLike(cursor) != 0;
    }

    public String spelling() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return LibClang.CXStrToString(
                Index_h.clang_getCursorSpelling(scope, cursor));
        }
    }

    public String USR() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return LibClang.CXStrToString(
                Index_h.clang_getCursorUSR(scope, cursor));
        }
    }

    public String prettyPrinted(PrintingPolicy policy) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return LibClang.CXStrToString(
                Index_h.clang_getCursorPrettyPrinted(scope, cursor, policy.ptr()));
        }
    }

    public String prettyPrinted() {
        try (PrintingPolicy policy = getPrintingPolicy()) {
            return prettyPrinted(policy);
        }
    }

    public String displayName() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return LibClang.CXStrToString(
                Index_h.clang_getCursorDisplayName(scope, cursor));
        }
    }

    public boolean equalCursor(Cursor other) {
        return Index_h.clang_equalCursors(cursor, other.cursor) != 0;
    }

    public Type type() {
        return new Type(Index_h.clang_getCursorType(ResourceScope.newImplicitScope(), cursor));
    }

    public Type getEnumDeclIntegerType() {
        return new Type(Index_h.clang_getEnumDeclIntegerType(ResourceScope.newImplicitScope(), cursor));
    }

    public boolean isEnumDeclScoped() {
        return Index_h.clang_EnumDecl_isScoped(cursor) != 0;
    }

    public Cursor getDefinition() {
        return new Cursor(Index_h.clang_getCursorDefinition(ResourceScope.newImplicitScope(), cursor));
    }

    public SourceLocation getSourceLocation() {
        MemorySegment loc = Index_h.clang_getCursorLocation(ResourceScope.newImplicitScope(), cursor);
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            if (Index_h.clang_equalLocations(loc, Index_h.clang_getNullLocation(scope)) != 0) {
                return null;
            }
        }
        return new SourceLocation(loc);
    }

    public SourceRange getExtent() {
        MemorySegment range = Index_h.clang_getCursorExtent(ResourceScope.newImplicitScope(), cursor);
        if (Index_h.clang_Range_isNull(range) != 0) {
            return null;
        }
        return new SourceRange(range);
    }

    public int numberOfArgs() {
        return Index_h.clang_Cursor_getNumArguments(cursor);
    }

    public Cursor getArgument(int idx) {
        return new Cursor(Index_h.clang_Cursor_getArgument(ResourceScope.newImplicitScope(), cursor, idx));
    }

    public int numberOfTemplateArgs() {
        return Index_h.clang_Cursor_getNumTemplateArguments(cursor);
    }

    public TemplateArgumentKind getTemplateArgumentKind(int idx) {
        int kind = Index_h.clang_Cursor_getTemplateArgumentKind(cursor, idx);
        return TemplateArgumentKind.valueOf(kind);
    }

    public Type getTemplateArgumentType(int idx) {
        return new Type(Index_h.clang_Cursor_getTemplateArgumentType(ResourceScope.newImplicitScope(), cursor, idx));
    }

    public long getTemplateArgumentValue(int idx) {
        return Index_h.clang_Cursor_getTemplateArgumentValue(cursor, idx);
    }

    public long getTemplateArgumentUnsignedValue(int idx) {
        return Index_h.clang_Cursor_getTemplateArgumentUnsignedValue(cursor, idx);
    }

    public CursorKind getTemplateCursorKind() {
        return CursorKind.valueOf(Index_h.clang_getTemplateCursorKind(cursor));
    }

    // C long long, 64-bit
    public long getEnumConstantValue() {
        return Index_h.clang_getEnumConstantDeclValue(cursor);
    }

    // C unsigned long long, 64-bit
    public long getEnumConstantUnsignedValue() {
        return Index_h.clang_getEnumConstantDeclUnsignedValue(cursor);
    }

    public boolean isBitField() {
        return Index_h.clang_Cursor_isBitField(cursor) != 0;
    }

    /**
     * Returns true if the base class specified by this cursor is virtual.
     */
    public boolean isVirtualBase() {
        return Index_h.clang_isVirtualBase(cursor) != 0;
    }

    public int getBitFieldWidth() {
        return Index_h.clang_getFieldDeclBitWidth(cursor);
    }

    public CursorKind kind() {
        return CursorKind.valueOf(kind);
    }

    public int kind0() {
        return kind;
    }

    /**
     * Determine if a C++ constructor is a converting constructor.
     */
    public boolean isConvertingConstructor() {
        return Index_h.clang_CXXConstructor_isConvertingConstructor(cursor) != 0;
    }

    /**
     * Determine if a C++ constructor is a copy constructor.
     */
    public boolean isCopyConstructor() {
        return Index_h.clang_CXXConstructor_isCopyConstructor(cursor) != 0;
    }

    /**
     * Determine if a C++ constructor is the default constructor.
     */
    public boolean isDefaultConstructor() {
        return Index_h.clang_CXXConstructor_isDefaultConstructor(cursor) != 0;
    }

    /**
     * Determine if a C++ constructor is a move constructor.
     */
    public boolean isMoveConstructor() {
        return Index_h.clang_CXXConstructor_isMoveConstructor(cursor) != 0;
    }

    /**
     * Determine if a C++ field is declared 'mutable'.
     */
    public boolean isMutableField() {
        return Index_h.clang_CXXField_isMutable(cursor) != 0;
    }

    /**
     * Determine if a C++ method is declared '= default'.
     */
    public boolean isDefaultedMethod() {
        return Index_h.clang_CXXMethod_isDefaulted(cursor) != 0;
    }

    /**
     * Determine if a C++ member function is pure virtual.
     */
    public boolean isPureVirtualMethod() {
        return Index_h.clang_CXXMethod_isPureVirtual(cursor) != 0;
    }

    /**
     * Determine if a C++ member function or member function template is
     * declared 'static'.
     */
    public boolean isStaticMethod() {
        return Index_h.clang_CXXMethod_isStatic(cursor) != 0;
    }

    /**
     * Determine if a C++ member function is explicitly declared 'virtual'
     * or if it overrides a virtual method from one of the base classes.
     */
    public boolean isVirtualMethod() {
        return Index_h.clang_CXXMethod_isVirtual(cursor) != 0;
    }

    public boolean isConstructor() {
        return kind() == CursorKind.Constructor;
    }

    public boolean isDestructor() {
        return kind() == CursorKind.Destructor;
    }

    /**
     * Determine if a C++ record is abstract, i.e. whether a class or struct
     * has a pure virtual member function.
     */
    public boolean isAbstractClass() {
        return Index_h.clang_CXXRecord_isAbstract(cursor) != 0;
    }

    /**
     * Determine if a C++ member function or member function template is
     * declared 'const'.
     */
    public boolean isConstMethod() {
        return Index_h.clang_CXXMethod_isConst(cursor) != 0;
    }

    public AccessSpecifier accessSpecifier() {
        int acc = Index_h.clang_getCXXAccessSpecifier(cursor);
        return AccessSpecifier.valueOf(acc);
    }

    /**
     * Determine the number of overloaded declarations referenced by a
     * \c CursorKind.OverloadedDeclRef cursor.
     *
     * \returns The number of overloaded declarations referenced by \c cursor. If it
     * is not a \c CursorKind.OverloadedDeclRef cursor, returns 0.
     */
    public int numberOfOverloadedDecls() {
        return Index_h.clang_getNumOverloadedDecls(cursor);
    }

    /**
     * Retrieve a cursor for one of the overloaded declarations referenced
     * by a \c CXCursor_OverloadedDeclRef cursor.
     *
     *
     * \param index The zero-based index into the set of overloaded declarations in
     * the cursor.
     *
     * \returns A cursor representing the declaration referenced by the given
     * \c cursor at the specified \c index. If the cursor does not have an
     * associated set of overloaded declarations, or if the index is out of bounds,
     * returns \c clang_getNullCursor();
     */
    public Cursor getOverloadedDecl(int index) {
        return new Cursor(Index_h.clang_getOverloadedDecl(ResourceScope.newImplicitScope(), cursor, index));
    }

    /**
     * For a cursor that is a reference, retrieve a cursor representing the entity that it references.
     */
    public Cursor getCursorReferenced() {
        return new Cursor(Index_h.clang_getCursorReferenced(ResourceScope.newImplicitScope(), cursor));
    }

    /**
     * Given a cursor that may represent a specialization or instantiation of a template,
     * retrieve the cursor that represents the template that it specializes or from which
     * it was instantiated.
     */
    public Cursor getSpecializedCursorTemplate() {
        return new Cursor(Index_h.clang_getSpecializedCursorTemplate(ResourceScope.newImplicitScope(), cursor));
    }

    private static class CursorChildren {
        private static final ArrayList<Cursor> children = new ArrayList<>();
        private static final MemoryAddress callback = Index_h.CXCursorVisitor.allocate((c, p, d) -> {
            MemorySegment copy = MemorySegment.allocateNative(c.byteSize(), ResourceScope.newImplicitScope());
            copy.copyFrom(c);
            Cursor cursor = new Cursor(copy);
            children.add(cursor);
            return Index_h.CXChildVisit_Continue();
        });

        synchronized static Stream<Cursor> get(Cursor c) {
            try {
                Index_h.clang_visitChildren(c.cursor, callback, MemoryAddress.NULL);
                return new ArrayList<>(children).stream();
            } finally {
                children.clear();
            }
        }
    }

    public Stream<Cursor> children() {
        return CursorChildren.get(this);
    }

    public Stream<Cursor> allChildren() {
        return children().flatMap(c -> Stream.concat(Stream.of(c), c.children()));
    }

    public String getMangling() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return LibClang.CXStrToString(
                Index_h.clang_Cursor_getMangling(scope, cursor));
        }
    }

    public TranslationUnit getTranslationUnit() {
        return new TranslationUnit(Index_h.clang_Cursor_getTranslationUnit(cursor));
    }

    private MemoryAddress eval0() {
        return Index_h.clang_Cursor_Evaluate(cursor);
    }

    public EvalResult eval() {
        MemoryAddress ptr = eval0();
        return ptr == MemoryAddress.NULL ? EvalResult.erroneous : new EvalResult(ptr);
    }

    public PrintingPolicy getPrintingPolicy() {
        return new PrintingPolicy(Index_h.clang_getCursorPrintingPolicy(cursor));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Cursor)) {
            return false;
        }
        return (Index_h.clang_equalCursors(cursor, ((Cursor)other).cursor) != 0);
    }

    @Override
    public int hashCode() {
        return spelling().hashCode();
    }
}
