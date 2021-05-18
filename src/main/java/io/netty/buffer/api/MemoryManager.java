/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer.api;

import java.lang.ref.Cleaner;

public interface MemoryManager {
    boolean isNative();
    Buffer allocateShared(AllocatorControl allocatorControl, long size, Drop<Buffer> drop, Cleaner cleaner);
    Buffer allocateConstChild(Buffer readOnlyConstParent);
    Drop<Buffer> drop();
    Object unwrapRecoverableMemory(Buffer buf);
    int capacityOfRecoverableMemory(Object memory);
    void discardRecoverableMemory(Object recoverableMemory);
    // todo should recoverMemory re-attach a cleaner?
    Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop);
    Object sliceMemory(Object memory, int offset, int length);
}
