/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer.api.pool;

import io.netty.buffer.api.AllocatorControl;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Drop;
import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.internal.Statics;

@SuppressWarnings("unchecked")
class UnpooledUnthetheredMemory implements AllocatorControl.UntetheredMemory {
    private final MemoryManager manager;
    private final Buffer buffer;

    UnpooledUnthetheredMemory(PooledBufferAllocator allocator, MemoryManager manager, int size) {
        this.manager = manager;
        PooledAllocatorControl allocatorControl = new PooledAllocatorControl();
        allocatorControl.parent = allocator;
        buffer = manager.allocateShared(allocatorControl, size, manager.drop(), Statics.CLEANER);
    }

    @Override
    public <Memory> Memory memory() {
        return (Memory) manager.unwrapRecoverableMemory(buffer);
    }

    @Override
    public <BufferType extends Buffer> Drop<BufferType> drop() {
        return (Drop<BufferType>) manager.drop();
    }
}
