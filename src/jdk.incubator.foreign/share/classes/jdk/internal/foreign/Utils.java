/*
 *  Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import jdk.incubator.foreign.*;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.VM;
import sun.invoke.util.Wrapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.Supplier;

import static sun.security.action.GetPropertyAction.*;

/**
 * This class contains misc helper functions to support creation of memory segments.
 */
public final class Utils {
    // used when testing invoke exact behavior of memory access handles
    private static final boolean SHOULD_ADAPT_HANDLES
        = Boolean.parseBoolean(privilegedGetProperty("jdk.internal.foreign.SHOULD_ADAPT_HANDLES", "true"));

    private static final MethodHandle SEGMENT_FILTER;
    private static final MethodHandle BYTE_TO_BOOL;
    private static final MethodHandle BOOL_TO_BYTE;
    public static final MethodHandle MH_bitsToBytesOrThrowForOffset;

    public static final Supplier<RuntimeException> bitsToBytesThrowOffset
        = () -> new UnsupportedOperationException("Cannot compute byte offset; bit offset is not a multiple of 8");

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SEGMENT_FILTER = lookup.findStatic(Utils.class, "filterSegment",
                    MethodType.methodType(MemorySegmentProxy.class, MemorySegment.class));
            BYTE_TO_BOOL = lookup.findStatic(Utils.class, "byteToBoolean",
                    MethodType.methodType(boolean.class, byte.class));
            BOOL_TO_BYTE = lookup.findStatic(Utils.class, "booleanToByte",
                    MethodType.methodType(byte.class, boolean.class));
            MH_bitsToBytesOrThrowForOffset = MethodHandles.insertArguments(
                lookup.findStatic(Utils.class, "bitsToBytesOrThrow",
                    MethodType.methodType(long.class, long.class, Supplier.class)),
                1,
                bitsToBytesThrowOffset);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    public static MemoryAddress alignUp(MemoryAddress ma, long alignment) {
        long offset = ma.toRawLongValue();
        return ma.addOffset(alignUp(offset, alignment) - offset);
    }

    public static MemorySegment alignUp(MemorySegment ms, long alignment) {
        long offset = ms.address().toRawLongValue();
        return ms.asSlice(alignUp(offset, alignment) - offset);
    }

    public static long bitsToBytesOrThrow(long bits, Supplier<RuntimeException> exFactory) {
        if (bits % 8 == 0) {
            return bits / 8;
        } else {
            throw exFactory.get();
        }
    }

    public static VarHandle makeMemoryAccessVarHandle(Class<?> carrier, boolean skipAlignmentCheck, long alignmentMask, ByteOrder order) {
        Class<?> baseCarrier = carrier;
        if (carrier == MemoryAddress.class) {
            baseCarrier = switch ((int)MemoryLayouts.ADDRESS.byteSize()) {
                case 8 -> long.class;
                case 4 -> int.class;
                default -> throw new UnsupportedOperationException("Unsupported address layout");
            };
        } else if (carrier == boolean.class) {
            baseCarrier = byte.class;
        }

        VarHandle handle = SharedSecrets.getJavaLangInvokeAccess().memoryAccessVarHandle(baseCarrier, skipAlignmentCheck, alignmentMask, order);

        // This adaptation is required, otherwise the memory access var handle will have type MemorySegmentProxy,
        // and not MemorySegment (which the user expects), which causes performance issues with asType() adaptations.
        handle = SHOULD_ADAPT_HANDLES
            ? MemoryHandles.filterCoordinates(handle, 0, SEGMENT_FILTER)
            : handle;
        if (carrier == boolean.class) {
            return MemoryHandles.filterValue(handle, BOOL_TO_BYTE, BYTE_TO_BOOL);
        } else if (carrier == MemoryAddress.class) {
            return MemoryHandles.asAddressVarHandle(handle);
        } else {
            return handle;
        }
    }

    private static MemorySegmentProxy filterSegment(MemorySegment segment) {
        return (AbstractMemorySegmentImpl)segment;
    }

    public static void checkPrimitiveCarrierCompat(Class<?> carrier, MemoryLayout layout) {
        checkLayoutType(layout, ValueLayout.class);
        if (!isValidPrimitiveCarrier(carrier) && carrier != MemoryAddress.class)
            throw new IllegalArgumentException("Unsupported carrier: " + carrier);
        if (carrier == MemoryAddress.class && layout.byteSize() != MemoryLayouts.ADDRESS.byteSize()) {
            throw new IllegalArgumentException("Address size mismatch: " + MemoryLayouts.ADDRESS.byteSize() + " != " + layout.bitSize());
        }
        if (carrier.isPrimitive() && Wrapper.forPrimitiveType(carrier).bitWidth() != layout.bitSize() &&
                carrier != boolean.class && layout.byteSize() != 1) {
            throw new IllegalArgumentException("Carrier size mismatch: " + carrier + " != " + layout);
        }
    }

    public static boolean isValidPrimitiveCarrier(Class<?> carrier) {
        return carrier == boolean.class
            || carrier == byte.class
            || carrier == short.class
            || carrier == char.class
            || carrier == int.class
            || carrier == long.class
            || carrier == float.class
            || carrier == double.class;
    }

    public static void checkLayoutType(MemoryLayout layout, Class<? extends MemoryLayout> layoutType) {
        if (!layoutType.isInstance(layout))
            throw new IllegalArgumentException("Expected a " + layoutType.getSimpleName() + ": " + layout);
    }

    private static boolean byteToBoolean(byte b) {
        return b != 0;
    }

    private static byte booleanToByte(boolean b) {
        return b ? (byte)1 : (byte)0;
    }
}
