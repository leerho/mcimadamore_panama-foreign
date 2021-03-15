/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.clang.libclang;
// Generated by jextract

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SegmentAllocator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.io.File;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static jdk.incubator.foreign.CLinker.*;

final class RuntimeHelper {
    private RuntimeHelper() {}
    private final static CLinker LINKER = CLinker.getInstance();
    private final static ClassLoader LOADER = RuntimeHelper.class.getClassLoader();
    private final static MethodHandles.Lookup MH_LOOKUP = MethodHandles.lookup();

    static <T> T requireNonNull(T obj, String symbolName) {
        if (obj == null) {
            throw new UnsatisfiedLinkError("unresolved symbol: " + symbolName);
        }
        return obj;
    }

    static final LibraryLookup[] libraries(String... libNames) {
        if (libNames.length == 0) {
            return new LibraryLookup[] { LibraryLookup.ofDefault() };
        } else {
            return Arrays.stream(libNames)
                 .map(libName -> {
                      if (libName.indexOf(File.separatorChar) != -1) {
                          return LibraryLookup.ofPath(Path.of(libName));
                      } else {
                          return LibraryLookup.ofLibrary(libName);
                      }
                 })
                .toArray(LibraryLookup[]::new);
        }
    }

    static final MemorySegment lookupGlobalVariable(LibraryLookup[] LIBRARIES, String name, MemoryLayout layout) {
        return lookup(LIBRARIES, name).map(s ->
            nonCloseableNonTransferableSegment(s.address().asSegmentRestricted(layout.byteSize()))).orElse(null);
    }

    final static SegmentAllocator DEFAULT_ALLOCATOR = MemorySegment::allocateNative;

    static final MethodHandle downcallHandle(LibraryLookup[] LIBRARIES, String name, String desc, FunctionDescriptor fdesc, boolean variadic) {
        return lookup(LIBRARIES, name).map(
                addr -> {
                    MethodType mt = MethodType.fromMethodDescriptorString(desc, LOADER);
                    return variadic ?
                        VarargsInvoker.make(addr, mt, fdesc) :
                        LINKER.downcallHandle(addr, DEFAULT_ALLOCATOR, mt, fdesc);
                }).orElse(null);
    }

    static final MethodHandle downcallHandle(LibraryLookup[] LIBRARIES, String name, String desc, FunctionDescriptor fdesc, boolean variadic, NativeScope scope) {
        return lookup(LIBRARIES, name).map(
                addr -> {
                    MethodType mt = MethodType.fromMethodDescriptorString(desc, LOADER);
                    return variadic ?
                            VarargsInvoker.make(addr, mt, fdesc) :
                            LINKER.downcallHandle(addr, scope, mt, fdesc);
                }).orElse(null);
    }

    static final <Z> MemorySegment upcallStub(Class<Z> fi, Z z, FunctionDescriptor fdesc, String mtypeDesc) {
        try {
            MethodHandle handle = MH_LOOKUP.findVirtual(fi, "apply",
                    MethodType.fromMethodDescriptorString(mtypeDesc, LOADER));
            handle = handle.bindTo(z);
            return LINKER.upcallStub(handle, fdesc);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final <Z> MemorySegment upcallStub(Class<Z> fi, Z z, FunctionDescriptor fdesc, String mtypeDesc, NativeScope scope) {
        try {
            MethodHandle handle = MH_LOOKUP.findVirtual(fi, "apply",
                    MethodType.fromMethodDescriptorString(mtypeDesc, LOADER));
            handle = handle.bindTo(z);
            return LINKER.upcallStub(handle, fdesc, scope.scope());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final MemorySegment nonCloseableNonTransferableSegment(MemorySegment seg) {
        return seg;
    }

    static MemorySegment asArrayRestricted(MemoryAddress addr, MemoryLayout layout, int numElements) {
        return nonCloseableSegment(addr.asSegmentRestricted(numElements * layout.byteSize()));
    }

    // Internals only below this point
    private static final MemorySegment nonCloseableSegment(MemorySegment seg) {
        return seg;
    }

    private static final Optional<LibraryLookup.Symbol> lookup(LibraryLookup[] LIBRARIES, String sym) {
        return Stream.of(LIBRARIES)
                .flatMap(l -> l.lookup(sym).stream())
                .findFirst();
    }

    private static class VarargsInvoker {
        private static final MethodHandle INVOKE_MH;
        private final Addressable symbol;
        private final MethodType varargs;
        private final FunctionDescriptor function;

        private VarargsInvoker(Addressable symbol, MethodType type, FunctionDescriptor function) {
            this.symbol = symbol;
            this.varargs = type;
            this.function = function;
        }

        static {
            try {
                INVOKE_MH = MethodHandles.lookup().findVirtual(VarargsInvoker.class, "invoke", MethodType.methodType(Object.class, Object[].class));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        static MethodHandle make(Addressable symbol, MethodType type, FunctionDescriptor function) {
            VarargsInvoker invoker = new VarargsInvoker(symbol, type, function);
            return INVOKE_MH.bindTo(invoker).asCollector(Object[].class, type.parameterCount())
                    .asType(type);
        }

        private Object invoke(Object[] args) throws Throwable {
            // one trailing Object[]
            int nNamedArgs = function.argumentLayouts().size();
            assert(args.length == nNamedArgs + 1);
            // The last argument is the array of vararg collector
            Object[] unnamedArgs = (Object[]) args[args.length - 1];

            int argsCount = nNamedArgs + unnamedArgs.length;
            Class<?>[] argTypes = new Class<?>[argsCount];
            MemoryLayout[] argLayouts = new MemoryLayout[nNamedArgs + unnamedArgs.length];

            int pos = 0;
            for (pos = 0; pos < nNamedArgs; pos++) {
                argTypes[pos] = varargs.parameterType(pos);
                argLayouts[pos] = function.argumentLayouts().get(pos);
            }

            assert pos == nNamedArgs;
            for (Object o: unnamedArgs) {
                argTypes[pos] = normalize(o.getClass());
                argLayouts[pos] = variadicLayout(argTypes[pos]);
                pos++;
            }
            assert pos == argsCount;

            MethodType mt = MethodType.methodType(varargs.returnType(), argTypes);
            FunctionDescriptor f = (function.returnLayout().isEmpty()) ?
                    FunctionDescriptor.ofVoid(argLayouts) :
                    FunctionDescriptor.of(function.returnLayout().get(), argLayouts);
            MethodHandle mh = LINKER.downcallHandle(symbol, mt, f);
            // flatten argument list so that it can be passed to an asSpreader MH
            Object[] allArgs = new Object[nNamedArgs + unnamedArgs.length];
            System.arraycopy(args, 0, allArgs, 0, nNamedArgs);
            System.arraycopy(unnamedArgs, 0, allArgs, nNamedArgs, unnamedArgs.length);

            return mh.asSpreader(Object[].class, argsCount).invoke(allArgs);
        }

        private static Class<?> unboxIfNeeded(Class<?> clazz) {
            if (clazz == Boolean.class) {
                return boolean.class;
            } else if (clazz == Void.class) {
                return void.class;
            } else if (clazz == Byte.class) {
                return byte.class;
            } else if (clazz == Character.class) {
                return char.class;
            } else if (clazz == Short.class) {
                return short.class;
            } else if (clazz == Integer.class) {
                return int.class;
            } else if (clazz == Long.class) {
                return long.class;
            } else if (clazz == Float.class) {
                return float.class;
            } else if (clazz == Double.class) {
                return double.class;
            } else {
                return clazz;
            }
        }

        private Class<?> promote(Class<?> c) {
            if (c == byte.class || c == char.class || c == short.class || c == int.class) {
                return long.class;
            } else if (c == float.class) {
                return double.class;
            } else {
                return c;
            }
        }

        private Class<?> normalize(Class<?> c) {
            c = unboxIfNeeded(c);
            if (c.isPrimitive()) {
                return promote(c);
            }
            if (MemoryAddress.class.isAssignableFrom(c)) {
                return MemoryAddress.class;
            }
            if (MemorySegment.class.isAssignableFrom(c)) {
                return MemorySegment.class;
            }
            throw new IllegalArgumentException("Invalid type for ABI: " + c.getTypeName());
        }

        private MemoryLayout variadicLayout(Class<?> c) {
            if (c == long.class) {
                return C_LONG_LONG;
            } else if (c == double.class) {
                return C_DOUBLE;
            } else if (MemoryAddress.class.isAssignableFrom(c)) {
                return C_POINTER;
            } else {
                throw new IllegalArgumentException("Unhandled variadic argument class: " + c);
            }
        }
    }
}
