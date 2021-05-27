/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.openjdk.bench.jdk.incubator.foreign.CallOverheadHelper.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign", "--enable-native-access=ALL-UNNAMED" })
public class CallOverheadConstant {

    MemoryAddress implicitAddr = MemoryAddress.NULL.address().asSegment(10, ResourceScope.newImplicitScope()).address();
    MemoryAddress sharedAddr = MemoryAddress.NULL.address().asSegment(10, ResourceScope.newSharedScope()).address();
    MemoryAddress confinedAddr = MemoryAddress.NULL.address().asSegment(10, ResourceScope.newConfinedScope()).address();

    @Benchmark
    public void jni_blank() throws Throwable {
        blank();
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        func.invokeExact();
    }

    @Benchmark
    public void panama_blank_trivial() throws Throwable {
        func_trivial.invokeExact();
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity.invokeExact(10);
    }

    @Benchmark
    public int panama_identity_trivial() throws Throwable {
        return (int) identity_trivial.invokeExact(10);
    }

    @Benchmark
    public MemorySegment panama_identity_struct() throws Throwable {
        return (MemorySegment) identity_struct.invokeExact(recycling_allocator, point);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_implicit() throws Throwable {
        return (MemoryAddress) identity_memory_address.invokeExact(implicitAddr);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_shared() throws Throwable {
        return (MemoryAddress) identity_memory_address.invokeExact(sharedAddr);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_confined() throws Throwable {
        return (MemoryAddress) identity_memory_address.invokeExact(confinedAddr);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_implicit_3() throws Throwable {
        return (MemoryAddress) identity_memory_address_3.invokeExact(implicitAddr, implicitAddr, implicitAddr);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_shared_3() throws Throwable {
        return (MemoryAddress) identity_memory_address_3.invokeExact(sharedAddr, sharedAddr, sharedAddr);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address_confined_3() throws Throwable {
        return (MemoryAddress) identity_memory_address_3.invokeExact(confinedAddr, confinedAddr, confinedAddr);
    }

    @Benchmark
    public void panama_args_01() throws Throwable {
        args1.invokeExact(10L);
    }

    @Benchmark
    public void panama_args_02() throws Throwable {
        args2.invokeExact(10L, 11D);
    }

    @Benchmark
    public void panama_args_03() throws Throwable {
        args3.invokeExact(10L, 11D, 12L);
    }

    @Benchmark
    public void panama_args_04() throws Throwable {
        args4.invokeExact(10L, 11D, 12L, 13D);
    }

    @Benchmark
    public void panama_args_05() throws Throwable {
        args5.invokeExact(10L, 11D, 12L, 13D, 14L);
    }

    @Benchmark
    public void panama_args_10() throws Throwable {
        args10.invokeExact(10L, 11D, 12L, 13D, 14L,
                           15D, 16L, 17D, 18L, 19D);
    }
}
