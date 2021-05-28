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
package io.netty.buffer.api.memseg;

import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.MemoryManagers;

public class SegmentMemoryManagers implements MemoryManagers {
    @Override
    public MemoryManager getHeapMemoryManager() {
        return new HeapMemorySegmentManager();
    }

    @Override
    public MemoryManager getNativeMemoryManager() {
        return new NativeMemorySegmentManager();
    }

    @Override
    public String getImplementationName() {
        return "MemorySegment";
    }

    @Override
    public String toString() {
        return "MS";
    }
}
