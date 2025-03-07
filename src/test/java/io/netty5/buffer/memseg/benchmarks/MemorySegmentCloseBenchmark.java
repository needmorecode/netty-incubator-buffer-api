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
package io.netty5.buffer.memseg.benchmarks;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 5, jvmArgsAppend = { "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MemorySegmentCloseBenchmark {
    @Param({"0", "10", "100"})
    public int unrelatedThreads;

    @Param({"16"/*, "124", "1024"*/})
    public int size;

    public ExecutorService unrelatedThreadPool;
    public byte[] array;

    @Setup
    public void setUp() {
        unrelatedThreadPool = unrelatedThreads > 0? Executors.newFixedThreadPool(unrelatedThreads) : null;
        array = new byte[size];
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        if (unrelatedThreadPool != null) {
            unrelatedThreadPool.shutdown();
            unrelatedThreadPool.awaitTermination(1, TimeUnit.MINUTES);
            unrelatedThreadPool = null;
        }
    }

    @Benchmark
    public MemorySegment nativeConfined() {
        try (MemorySession scope = MemorySession.openConfined()) {
            return MemorySegment.allocateNative(size, scope);
        }
    }

    @Benchmark
    public MemorySegment nativeShared() {
        try (MemorySession scope = MemorySession.openShared()) {
            return MemorySegment.allocateNative(size, scope);
        }
    }
}
