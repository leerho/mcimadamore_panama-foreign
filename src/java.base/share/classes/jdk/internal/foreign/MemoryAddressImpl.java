/*
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.internal.foreign;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;

import java.foreign.MemoryAddress;
import java.foreign.MemoryScope;
import java.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

public class MemoryAddressImpl implements MemoryAddress {

    static Unsafe UNSAFE;
    static final int BYTE_ARR_BASE;

    static {
        if (MemoryAddressImpl.class.getClassLoader() != null) {
            throw new IllegalStateException();
        }
        UNSAFE = Unsafe.getUnsafe();
        BYTE_ARR_BASE = UNSAFE.arrayBaseOffset(byte[].class);
    }

    private final MemorySegmentImpl segment;
    private final long offset;

    public MemoryAddressImpl(MemorySegmentImpl segment) {
        this(segment, 0);
    }

    public MemoryAddressImpl(MemorySegmentImpl segment, long offset) {
        this.segment = Objects.requireNonNull(segment);
        this.offset = offset;
    }

    public static void copy(MemoryAddressImpl src, MemoryAddressImpl dst, long size) {
        src.segment.checkAlive();
        dst.segment.checkAlive();
        src.checkAccess(0, size, true);
        dst.checkAccess(0, size, false);
        UNSAFE.copyMemory(
                src.unsafeGetBase(), src.unsafeGetOffset(),
                dst.unsafeGetBase(), dst.unsafeGetOffset(),
                size);
    }

    public long size() {
        return segment.length;
    }

    public long offset() {
        return offset;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public MemoryAddress offset(long bytes) {
        return new MemoryAddressImpl(segment, offset + bytes);
    }

    public void checkAccess(long offset, long length, boolean readOnly) {
        if (segment().scope() != null) {
            if (!readOnly && (segment.scope().characteristics() & MemoryScope.IMMUTABLE) != 0) {
                throw new IllegalStateException("Attempting to write memory in immutable scope");
            }
            segment.checkAlive();
        }
        segment.checkRange(this.offset + offset, length);
    }

    public long unsafeGetOffset() {
        return segment.min + offset;
    }

    public Object unsafeGetBase() {
        return segment.base;
    }

    @Override
    public ByteBuffer asByteBuffer(int bytes) throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException {
        boolean readOnly = (segment().scope().characteristics() & MemoryScope.IMMUTABLE) != 0;
        segment.checkAlive();
        segment.resize(this.offset, bytes); //throws IAE if out of bounds
        checkAccess(0L, bytes, readOnly);
        JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();
        Object base = unsafeGetBase();
        long offset = unsafeGetOffset();
        ByteBuffer _bb;
        if (base != null) {
            if (!(base instanceof byte[])) {
                throw new UnsupportedOperationException("Not an address to an heap-allocated byte array");
            } else if (offset > Integer.MAX_VALUE) {
                //we should not get here
                throw new AssertionError("Offset is too large");
            }
            _bb = ByteBuffer.wrap((byte[])base, (int)offset - BYTE_ARR_BASE, bytes);
        } else {
            _bb = nioAccess.newDirectByteBuffer(offset, bytes, null);
        }
        if (readOnly) {
            //scope is IMMUTABLE - obtain a RO byte buffer
            _bb = _bb.asReadOnlyBuffer();
        }
        if ((segment.scope().characteristics() & MemoryScope.PINNED) == 0) {
            //scope is not PINNED - need to wrap the buffer so that appropriate scope checks take place
            _bb = nioAccess.newScopedByteBuffer(segment.scope, _bb);
        }
        return _bb;
    }

    public static long addressof(MemoryAddress address) {
        MemoryAddressImpl addressImpl = (MemoryAddressImpl)address;
        addressImpl.checkAccess(0L, 1, false);
        if (addressImpl.unsafeGetBase() != null) {
            throw new IllegalStateException("Heap address!");
        }
        return addressImpl.unsafeGetOffset();
    }

    @Override
    public int hashCode() {
        return Objects.hash(unsafeGetBase(), unsafeGetOffset());
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof MemoryAddressImpl) {
            MemoryAddressImpl addr = (MemoryAddressImpl)that;
            return Objects.equals(unsafeGetBase(), ((MemoryAddressImpl) that).unsafeGetBase()) &&
                    unsafeGetOffset() == addr.unsafeGetOffset();
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "MemoryAddress{ region: " + segment + " offset=0x" + Long.toHexString(offset) + " }";
    }
}
