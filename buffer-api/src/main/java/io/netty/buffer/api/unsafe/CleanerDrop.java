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
package io.netty.buffer.api.unsafe;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Drop;
import io.netty.util.internal.PlatformDependent;

import java.lang.ref.Cleaner;

public class CleanerDrop implements Drop<Buffer> {
    private final Drop<Buffer> drop;

    public CleanerDrop(UnsafeMemory memory, Drop<Buffer> drop, Cleaner cleaner) {
        this.drop = drop;
        long address = memory.address;
        cleaner.register(memory, new FreeAddress(address));
    }

    @Override
    public void drop(Buffer obj) {
        drop.drop(obj);
    }

    @Override
    public void attach(Buffer obj) {
        drop.attach(obj);
    }

    private static class FreeAddress implements Runnable {
        private final long address;

        FreeAddress(long address) {
            this.address = address;
        }

        @Override
        public void run() {
            PlatformDependent.freeMemory(address);
        }
    }
}
