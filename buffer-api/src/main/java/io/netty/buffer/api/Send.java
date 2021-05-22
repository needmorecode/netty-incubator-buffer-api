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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Send object is a temporary holder of an {@link Rc}, used for transferring the ownership of the Rc from one thread
 * to another.
 * <p>
 * Prior to the Send being created, the originating Rc is invalidated, to prevent access while it is being sent. This
 * means it cannot be accessed, closed, or disposed of, while it is in-flight. Once the Rc is {@linkplain #receive()
 * received}, the new ownership is established.
 * <p>
 * Care must be taken to ensure that the Rc is always received by some thread. Failure to do so can result in a resource
 * leak.
 *
 * @param <T>
 */
public interface Send<T extends Rc<T>> {
    /**
     * Construct a {@link Send} based on the given {@link Supplier}.
     * The supplier will be called only once, in the receiving thread.
     *
     * @param concreteObjectType The concrete type of the object being sent. Specifically, the object returned from the
     *                           {@link Supplier#get()} method must be an instance of this class.
     * @param supplier The supplier of the object being sent, which will be called when the object is ready to be
     *                received.
     * @param <T> The type of object being sent.
     * @return A {@link Send} which will deliver an object of the given type, from the supplier.
     */
    static <T extends Rc<T>> Send<T> sending(Class<T> concreteObjectType, Supplier<? extends T> supplier) {
        return new Send<T>() {
            private final AtomicBoolean gate = new AtomicBoolean();
            @Override
            public T receive() {
                if (gate.getAndSet(true)) {
                    throw new IllegalStateException("This object has already been received.");
                }
                return supplier.get();
            }

            @Override
            public boolean referentIsInstanceOf(Class<?> cls) {
                return cls.isAssignableFrom(concreteObjectType);
            }

            @Override
            public void discard() {
                if (!gate.getAndSet(true)) {
                    supplier.get().close();
                }
            }
        };
    }

    /**
     * Determine if the given candidate object is an instance of a {@link Send} from which an object of the given type
     * can be received.
     *
     * @param type The type of object we wish to receive.
     * @param candidate The candidate object that might be a {@link Send} of an object of the given type.
     * @return {@code true} if the candidate object is a {@link Send} that would deliver an object of the given type,
     * otherwise {@code false}.
     */
    static boolean isSendOf(Class<?> type, Object candidate) {
        return candidate instanceof Send && ((Send<?>) candidate).referentIsInstanceOf(type);
    }

    /**
     * Receive the {@link Rc} instance being sent, and bind its ownership to the calling thread. The invalidation of the
     * sent Rc in the sending thread happens-before the return of this method.
     * <p>
     * This method can only be called once, and will throw otherwise.
     *
     * @return The sent Rc instance.
     * @throws IllegalStateException If this method is called more than once.
     */
    T receive();

    /**
     * Apply a mapping function to the object being sent. The mapping will occur when the object is received.
     *
     * @param type The result type of the mapping function.
     * @param mapper The mapping function to apply to the object being sent.
     * @param <R> The result type of the mapping function.
     * @return A new {@link Send} instance that will deliver an object that is the result of the mapping.
     */
    default <R extends Rc<R>> Send<R> map(Class<R> type, Function<T, R> mapper) {
        return sending(type, () -> mapper.apply(receive()));
    }

    /**
     * Discard this {@link Send} and the object it contains.
     * This has no effect if the send has already been received.
     */
    default void discard() {
        try {
            receive().close();
        } catch (IllegalStateException ignore) {
            // Don't do anything if the send has already been consumed.
        }
    }

    /**
     * Determine if the object received from this {@code Send} is an instance of the given class.
     *
     * @param cls The type to check.
     * @return {@code true} if the object received from this {@code Send} can be assigned fields or variables of the
     * given type, otherwise false.
     */
    boolean referentIsInstanceOf(Class<?> cls);
}
