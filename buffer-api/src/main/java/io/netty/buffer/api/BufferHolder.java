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

import io.netty.buffer.api.internal.ResourceSupport;
import io.netty.buffer.api.internal.Statics;

import java.lang.invoke.VarHandle;
import java.util.Objects;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * The {@link BufferHolder} is an abstract class that simplifies the implementation of objects that themselves contain
 * a {@link Buffer} instance.
 * <p>
 * The {@link BufferHolder} can only hold on to a single buffer, so objects and classes that need to hold on to multiple
 * buffers will have to do their implementation from scratch, though they can use the code of the {@link BufferHolder}
 * as inspiration.
 * <p>
 * If you just want an object that is a reference to a buffer, then the {@link BufferRef} can be used for that purpose.
 * If you have an advanced use case where you wish to implement {@link Resource}, and tightly control lifetimes, then
 * {@link ResourceSupport} can be of help.
 *
 * @param <T> The concrete {@link BufferHolder} type.
 */
public abstract class BufferHolder<T extends BufferHolder<T>> implements Resource<T> {
    private static final VarHandle BUF = Statics.findVarHandle(lookup(), BufferHolder.class, "buf", Buffer.class);
    private Buffer buf;

    /**
     * Create a new {@link BufferHolder} to hold the given {@linkplain Buffer buffer}.
     * <p>
     * <strong>Note:</strong> this increases the reference count of the given buffer.
     *
     * @param buf The {@linkplain Buffer buffer} to be held by this holder.
     */
    protected BufferHolder(Buffer buf) {
        this.buf = Objects.requireNonNull(buf, "The buffer cannot be null.");
    }

    /**
     * Create a new {@link BufferHolder} to hold the {@linkplain Buffer buffer} received from the given {@link Send}.
     * <p>
     * The {@link BufferHolder} will then be holding exclusive ownership of the buffer.
     *
     * @param send The {@linkplain Buffer buffer} to be held by this holder.
     */
    protected BufferHolder(Send<Buffer> send) {
        buf = Objects.requireNonNull(send, "The send cannot be null.").receive();
    }

    @Override
    public void close() {
        buf.close();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Send<T> send() {
        return buf.send().map((Class<T>) getClass(), this::receive);
    }

    /**
     * Called when a {@linkplain #send() sent} {@link BufferHolder} is received by the recipient.
     * The {@link BufferHolder} should return a new concrete instance, that wraps the given {@link Buffer} object.
     *
     * @param buf The {@link Buffer} that is {@linkplain Send#receive() received} by the recipient,
     *           and needs to be wrapped in a new {@link BufferHolder} instance.
     * @return A new {@linkplain T buffer holder} instance, containing the given {@linkplain Buffer buffer}.
     */
    protected abstract T receive(Buffer buf);

    /**
     * Replace the underlying referenced buffer with the given buffer.
     * <p>
     * This method is protected to permit advanced use cases of {@link BufferHolder} sub-class implementations.
     * <p>
     * <strong>Note:</strong> this method decreases the reference count of the current buffer,
     * and takes exclusive ownership of the sent buffer.
     * <p>
     * The buffer assignment is performed using a plain store.
     *
     * @param send The new {@link Buffer} instance that is replacing the currently held buffer.
     */
    protected final void replaceBuffer(Send<Buffer> send) {
        buf.close();
        buf = send.receive();
    }

    /**
     * Replace the underlying referenced buffer with the given buffer.
     * <p>
     * This method is protected to permit advanced use cases of {@link BufferHolder} sub-class implementations.
     * <p>
     * <strong>Note:</strong> this method decreases the reference count of the current buffer,
     * and takes exclusive ownership of the sent buffer.
     * <p>
     * The buffer assignment is performed using a volatile store.
     *
     * @param send The {@link Send} with the new {@link Buffer} instance that is replacing the currently held buffer.
     */
    protected final void replaceBufferVolatile(Send<Buffer> send) {
        var prev = (Buffer) BUF.getAndSet(this, send.receive());
        prev.close();
    }

    /**
     * Access the held {@link Buffer} instance.
     * <p>
     * The access is performed using a plain load.
     *
     * @return The {@link Buffer} instance being held by this {@linkplain T buffer holder}.
     */
    protected final Buffer getBuffer() {
        return buf;
    }

    /**
     * Access the held {@link Buffer} instance.
     * <p>
     * The access is performed using a volatile load.
     *
     * @return The {@link Buffer} instance being held by this {@linkplain T buffer holder}.
     */
    protected final Buffer getBufferVolatile() {
        return (Buffer) BUF.getVolatile(this);
    }

    @Override
    public boolean isAccessible() {
        return buf.isAccessible();
    }
}
