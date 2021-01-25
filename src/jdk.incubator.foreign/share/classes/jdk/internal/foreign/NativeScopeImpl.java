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

package jdk.internal.foreign;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;
import jdk.incubator.foreign.ResourceScope;

import java.util.OptionalLong;
import java.util.function.Function;

public class NativeScopeImpl implements NativeScope {
    ResourceScope publicScope = ResourceScope.ofConfined();
    ResourceScope.Lock lock = publicScope.lock();
    AbstractArenaAllocator allocator;

    public NativeScopeImpl(Function<ResourceScope, AbstractArenaAllocator> allocatorFactory) {
        this.allocator = allocatorFactory.apply(publicScope);
    }

    @Override
    public MemorySegment allocate(long bytesSize, long bytesAlignment) {
        return allocator.allocate(bytesSize, bytesAlignment);
    }

    @Override
    public OptionalLong byteSize() {
        return allocator.size();
    }

    @Override
    public long allocatedBytes() {
        return allocator.allocatedBytes();
    }

    @Override
    public void close() {
        lock.close();
        publicScope.close();
    }

    @Override
    public boolean isAlive() {
        return publicScope.isAlive();
    }

    @Override
    public boolean isCloseable() {
        return publicScope.isCloseable();
    }

    @Override
    public Thread ownerThread() {
        return publicScope.ownerThread();
    }

    @Override
    public Lock lock() {
        return publicScope.lock();
    }

    @Override
    public void addOnClose(Runnable runnable) {
        publicScope.addOnClose(runnable);
    }

    public MemoryScope scope() {
        return (MemoryScope)publicScope;
    }
}