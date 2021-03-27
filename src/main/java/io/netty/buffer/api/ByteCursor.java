/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.api;

import io.netty.util.ByteProcessor;

/**
 * The ByteCursor scans through a sequence of bytes.
 * This is similar to {@link ByteProcessor}, but for external iteration rather than internal iteration.
 * The external iteration allows the callers to control the pace of the iteration.
 * The API includes methods for reading {@code long}s as a batch of 8 bytes.
 * The long values are always in big-endian format, so that the highest-order byte in the long value, contain the byte
 * that would otherwise have been returned by the next call to {@link #getByte()}.
 */
public interface ByteCursor {
    /**
     * Check if the iterator has at least 8 bytes left, and if so, read those 8 bytes and move the cursor forward.
     * The bytes are packed as a {@code long} value in big-endian format, such that the highest-order byte
     * in the long, is the byte that would otherwise have been returned by the next call to {@link #getByte()},
     * after a call to {@link #readByte()}.
     * The bytes (as a {@code long}) will then be available through the {@link #getLong()} method.
     * <p>
     * Note that when this method returns {@code false}, the {@link #readByte()} can still return {@code true}.
     * It is recommended to have any long-processing loop be followed by a byte-processing loop for the 7 or fewer
     * bytes that might form a tail in the cursor.
     * <p>
     * Also note that this method will not influence what is returned the {@link #getByte()} method.
     *
     * @return {@code true} if the cursor read 8 bytes and moved forward, otherwise {@code false}.
     */
    boolean readLong();

    /**
     * Return the last 8 bytes read by {@link #readLong()}.
     * If {@link #readLong()} has not been called on this cursor before, then {@code -1} is returned.
     * <p>
     * The long value is in the big-endian format, such that the highest-order by of the long value, is the byte that
     * would otherwise have been produced by a {@link #readByte()} / {@link #getByte()} pair.
     * This means that cursors that iterate in reverse, e.g. from {@link Buffer#openReverseCursor()}, return longs in a
     * "mirrored" or "reversed" big-endian format.
     *
     * @return The 8 bytes, in big-endian format, that was read by the most recent successful call to
     * {@link #readLong()}.
     */
    long getLong();

    /**
     * Check if the iterator has at least one byte left, and if so, read that byte and move the cursor forward.
     * The byte will then be available through the {@link #getByte()}.
     * <p>
     * Note that this method will not influence what is returned from the {@link #getLong()} method.
     *
     * @return {@code true} if the cursor read a byte and moved forward, otherwise {@code false}.
     */
    boolean readByte();

    /**
     * Return the last byte that was read by {@link #readByte()}.
     * If {@link #readByte()} has not been called on this cursor before, then {@code -1} is returned.
     *
     * @return The next byte that was read by the most recent successful call to {@link #readByte()}.
     */
    byte getByte();

    /**
     * The current position of this iterator into the underlying sequence of bytes.
     * For instance, if we are iterating a buffer, this would be the iterators current offset into the buffer.
     *
     * @return The current iterator offset into the underlying sequence of bytes.
     */
    int currentOffset();

    /**
     * Get the current number of bytes left in the iterator.
     *
     * @return The number of bytes left in the iterator.
     */
    int bytesLeft();

    /**
     * Process the remaining bytes in this iterator with the given {@link ByteProcessor}.
     * This method consumes the iterator.
     *
     * @param processor The processor to use for processing the bytes in the iterator.
     * @return The number of bytes processed, if the {@link ByteProcessor#process(byte) process} method returned
     * {@code false}, or {@code -1} if the whole iterator was processed.
     */
    default int process(ByteProcessor processor) {
        boolean requestMore = true;
        int count = 0;
        while (readByte() && (requestMore = processor.process(getByte()))) {
            count++;
        }
        return requestMore? -1 : count;
    }
}
