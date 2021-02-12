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

import java.nio.ByteBuffer;

/**
 * A view onto the buffer component being processed in a given iteration of
 * {@link Buffer#forEachReadable(int, ReadableComponentProcessor)}.
 */
public interface ReadableComponent {

    /**
     * Check if this component is backed by a cached byte array than can be accessed cheaply.
     * <p>
     * <strong>Note</strong> that regardless of what this method returns, the array should not be used to modify the
     * contents of this buffer component.
     *
     * @return {@code true} if {@link #readableArray()} is a cheap operation, otherwise {@code false}.
     */
    boolean hasReadableArray();

    /**
     * Get a byte array of the contents of this component.
     * <p>
     * <strong>Note</strong> that the array is meant to be read-only. It may either be a direct reference to the
     * concrete array instance that is backing this component, or it is a fresh copy. Writing to the array may produce
     * undefined behaviour.
     *
     * @return A byte array of the contents of this component.
     * @throws UnsupportedOperationException if {@link #hasReadableArray()} returns {@code false}.
     */
    byte[] readableArray();

    /**
     * An offset into the {@link #readableArray()} where this component starts.
     *
     * @return An offset into {@link #readableArray()}.
     * @throws UnsupportedOperationException if {@link #hasReadableArray()} returns {@code false}.
     */
    int readableArrayOffset();

    /**
     * Give the native memory address backing this buffer, or return 0 if this buffer has no native memory address.
     * <p>
     * <strong>Note</strong> that the address should not be used for writing to the buffer memory, and doing so may
     * produce undefined behaviour.
     *
     * @return The native memory address, if any, otherwise 0.
     */
    long readableNativeAddress();

    /**
     * Get a {@link ByteBuffer} instance for this memory component.
     * <p>
     * <strong>Note</strong> that the {@link ByteBuffer} is read-only, to prevent write accesses to the memory,
     * when the buffer component is obtained through {@link Buffer#forEachReadable(int, ReadableComponentProcessor)}.
     *
     * @return A new {@link ByteBuffer}, with its own position and limit, for this memory component.
     */
    ByteBuffer readableBuffer();
    // todo for Unsafe-based impl, DBB.attachment needs to keep underlying memory alive
}
