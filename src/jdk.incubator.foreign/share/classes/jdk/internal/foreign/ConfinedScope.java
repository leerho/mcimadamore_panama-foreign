/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.ResourceScope;

import java.lang.ref.Cleaner;

public class ConfinedScope extends ResourceScopeImpl implements Runnable {

    boolean closed;
    final Thread ownerThread;
    int lockCount;
    final ResourceList resourceList = new ResourceList();
    ResourceList resourceListToAdd = resourceList;

    public ConfinedScope(Thread ownerThread, Cleaner cleaner) {
        this.ownerThread = ownerThread;
        if (cleaner != null) {
            var localRef = resourceList;
            cleaner.register(this, localRef::cleanup); // non-capturing
        }
    }

    @Override
    public boolean isAlive() {
        return !closed;
    }

    @Override
    public Thread ownerThread() {
        return ownerThread;
    }

    @Override
    public boolean isImplicit() {
        return false;
    }

    @Override
    public void close() {
        checkValidState();
        if (lockCount > 0) {
            throw new IllegalStateException("Scope is acquired by " + lockCount + " locks");
        }
        closed = true;
        resourceList.cleanup();
    }

    @Override
    public void addCloseAction(Runnable runnable) {
        checkValidState();
        resourceListToAdd = resourceListToAdd.add(runnable);
    }

    @Override
    public void bindTo(ResourceScope scope) {
        checkValidState();
        acquire();
        scope.addCloseAction(this);
    }

    @Override
    public void checkValidState() {
        if (ownerThread != Thread.currentThread()) {
            throw new IllegalStateException("Attempted access outside owning thread");
        }
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
    }

    private void acquire() {
        lockCount++;
    }

    private void release() {
        lockCount--;
    }

    @Override
    public void run() {
        release();
    }

}
