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

class PooledAllocatorControl implements AllocatorControl {
    public PooledBufferAllocator parent;
    public PoolArena arena;
    public PoolChunk chunk;
    public PoolThreadCache threadCache;
    public long handle;
    public int normSize;

    @Override
    public UntetheredMemory allocateUntethered(Buffer originator, int size) {
        return parent.allocate(this, size);
    }

    @Override
    public void recoverMemory(Object memory) {
        arena.free(chunk, handle, normSize, threadCache);
    }
}
