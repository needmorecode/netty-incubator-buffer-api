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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.Drop;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A drop implementation that delegates to another drop instance, either when called directly, or when it becomes
 * cleanable. This ensures that objects are dropped even if they leak.
 */
public final class CleanerDrop<T> implements Drop<T> {
    private Cleaner.Cleanable cleanable;
    private GatedRunner<T> runner;

    /**
     * Wrap the given drop instance, and produce a new drop instance that will also call the delegate drop instance if
     * it becomes cleanable.
     */
    public static <T> Drop<T> wrap(Drop<T> drop) {
        CleanerDrop<T> cleanerDrop = new CleanerDrop<>();
        GatedRunner<T> runner = new GatedRunner<>(drop);
        cleanerDrop.cleanable = Statics.CLEANER.register(cleanerDrop, runner);
        cleanerDrop.runner = runner;
        return cleanerDrop;
    }

    private CleanerDrop() {
    }

    @Override
    public void attach(T obj) {
        runner.set(obj);
        runner.drop.attach(obj);
    }

    @Override
    public void drop(T obj) {
        attach(obj);
        cleanable.clean();
    }

    @Override
    public String toString() {
        return "CleanerDrop(" + runner.drop + ')';
    }

    private static final class GatedRunner<T> extends AtomicReference<T> implements Runnable {
        private static final long serialVersionUID = 2685535951915798850L;
        final Drop<T> drop;

        private GatedRunner(Drop<T> drop) {
            this.drop = drop;
        }

        @Override
        public void run() {
            T obj = getAndSet(null); // Make absolutely sure we only delegate once.
            if (obj != null) {
                drop.drop(obj);
            }
        }
    }
}
