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
package io.netty.buffer.api;

import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The MemoryManagers interface is the handle through which {@link BufferAllocator buffer allocators} access the low
 * level memory management APIs.
 * <p>
 * This is hidden behind this interface in order to make allocation and pool agnostic and reusable across buffer and
 * memory implementations.
 */
public interface MemoryManagers {
    /**
     * Get the default, or currently configured, memory managers instance.
     * @return A MemoryManagers instance.
     */
    static MemoryManagers getManagers() {
        return MemoryManagersOverride.getManagers();
    }

    /**
     * Temporarily override the default configured memory managers instance.
     * <p>
     * Calls to {@link #getManagers()} from within the given supplier will get the given managers instance.
     *
     * @param managers Override the default configured managers instance with this instance.
     * @param supplier The supplier function to be called while the override is in place.
     * @param <T> The result type from the supplier.
     * @return The result from the supplier.
     */
    static <T> T using(MemoryManagers managers, Supplier<T> supplier) {
        return MemoryManagersOverride.using(managers, supplier);
    }

    /**
     * Get a lazy-loading stream of all available memory managers.
     *
     * @return A stream of providers of memory managers instances.
     */
    static Stream<ServiceLoader.Provider<MemoryManagers>> getAllManagers() {
        var loader = ServiceLoader.load(MemoryManagers.class);
        return loader.stream();
    }

    /**
     * Get a {@link MemoryManager} instance that is suitable for allocating on-heap {@link Buffer} instances.
     *
     * @return An on-heap {@link MemoryManager}.
     */
    MemoryManager getHeapMemoryManager();

    /**
     * Get a {@link MemoryManager} instance that is suitable for allocating off-heap {@link Buffer} instances.
     *
     * @return An off-heap {@link MemoryManager}.
     */
    MemoryManager getNativeMemoryManager();
}
