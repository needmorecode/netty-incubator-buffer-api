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

import io.netty.buffer.api.pool.PooledBufferAllocator;

import java.nio.ByteOrder;
import java.util.function.Supplier;

/**
 * Interface for {@link Buffer} allocators.
 */
public interface BufferAllocator extends AutoCloseable {
    /**
     * Check that the given {@code size} argument is a valid buffer size, or throw an {@link IllegalArgumentException}.
     *
     * @param size The size to check.
     * @throws IllegalArgumentException if the size is not possitive, or if the size is too big (over ~2 GB) for a
     * buffer to accomodate.
     */
    static void checkSize(long size) {
        if (size < 1) {
            throw new IllegalArgumentException("Buffer size must be positive, but was " + size + '.');
        }
        // We use max array size because on-heap buffers will be backed by byte-arrays.
        int maxArraySize = Integer.MAX_VALUE - 8;
        if (size > maxArraySize) {
            throw new IllegalArgumentException(
                    "Buffer size cannot be greater than " + maxArraySize + ", but was " + size + '.');
        }
    }

    /**
     * Allocate a {@link Buffer} of the given size in bytes. This method may throw an {@link OutOfMemoryError} if there
     * is not enough free memory available to allocate a {@link Buffer} of the requested size.
     * <p>
     * The buffer will use the current platform native byte order by default, for accessor methods that don't have an
     * explicit byte order.
     *
     * @param size The size of {@link Buffer} to allocate.
     * @return The newly allocated {@link Buffer}.
     */
    Buffer allocate(int size);

    /**
     * Allocate a {@link Buffer} of the given size in bytes. This method may throw an {@link OutOfMemoryError} if there
     * is not enough free memory available to allocate a {@link Buffer} of the requested size.
     * <p>
     * The buffer will use the given byte order by default.
     *
     * @param size The size of {@link Buffer} to allocate.
     * @param order The default byte order used by the accessor methods that don't have an explicit byte order.
     * @return The newly allocated {@link Buffer}.
     */
    default Buffer allocate(int size, ByteOrder order) {
        return allocate(size).order(order);
    }

    /**
     * Create a supplier of "constant" {@linkplain Buffer Buffers} from this allocator, that all have the given
     * byte contents. The buffer has the same capacity as the byte array length, and its write offset is placed at the
     * end, and its read offset is at the beginning, such that the entire buffer contents are readable.
     * <p>
     * The buffers produced by the supplier will have {@linkplain Buffer#isOwned() ownership}, and closing them will
     * make them {@linkplain Buffer#isAccessible() inaccessible}, just like a normally allocated buffer.
     * <p>
     * The buffers produced are "constants", in the sense that they are {@linkplain Buffer#readOnly() read-only}.
     * <p>
     * It can generally be expected, but is not guaranteed, that the returned supplier is more resource efficient than
     * allocating and copying memory with other available APIs. In such optimised implementations, the underlying memory
     * baking the buffers will be shared among all the buffers produced by the supplier.
     * <p>
     * The primary use case for this API, is when you need to repeatedly produce buffers with the same contents, and
     * you perhaps wish to keep a {@code static final} field with these contents. This use case has previously been
     * solved by allocating a read-only buffer with the given contents, and then slicing or duplicating it on every use.
     * This approach had several problems. For instance, if you forget to slice, the offsets of the buffer can change
     * in unexpected ways, since the same buffer instance is shared and accessed from many places. The buffer could also
     * be deallocated, making the data inaccessible. The supplier-based API solves all of these problems, by enforcing
     * that each usage get their own distinct buffer instance.
     *
     * @param bytes The byte contents of the buffers produced by the returned supplier.
     * @return A supplier of read-only buffers with the given contents.
     */
    default Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        byte[] safeCopy = bytes.clone(); // Prevent modifying the bytes after creating the supplier.
        return () -> allocate(bytes.length).writeBytes(safeCopy).makeReadOnly();
    }

    /**
     * Close this allocator, freeing all of its internal resources.
     * <p>
     * Existing (currently in-use) allocated buffers will not be impacted by calling this method.
     * If this is a pooling or caching allocator, then existing buffers will be immediately freed when they are closed,
     * instead of being pooled or cached.
     * <p>
     * The allocator can still be used to allocate more buffers after calling this method.
     * However, if this is a pooling or caching allocator, then the pooling and caching functionality will be
     * effectively disabled after calling this method.
     * <p>
     * If this allocator does not perform any pooling or caching, then calling this method likely has no effect.
     */
    @Override
    default void close() {
    }

    static BufferAllocator heap() {
        return new ManagedBufferAllocator(MemoryManagers.getManagers().getHeapMemoryManager());
    }

    static BufferAllocator direct() {
        return new ManagedBufferAllocator(MemoryManagers.getManagers().getNativeMemoryManager());
    }

    static BufferAllocator pooledHeap() {
        return new PooledBufferAllocator(MemoryManagers.getManagers().getHeapMemoryManager());
//        return new SizeClassedMemoryPool(MemoryManagers.getManagers().getHeapMemoryManager());
    }

    static BufferAllocator pooledDirect() {
        return new SizeClassedMemoryPool(MemoryManagers.getManagers().getNativeMemoryManager());
    }
}
