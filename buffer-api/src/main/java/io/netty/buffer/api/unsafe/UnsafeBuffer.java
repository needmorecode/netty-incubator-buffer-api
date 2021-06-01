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
package io.netty.buffer.api.unsafe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.AllocatorControl;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferReadOnlyException;
import io.netty.buffer.api.ByteCursor;
import io.netty.buffer.api.Drop;
import io.netty.buffer.api.Owned;
import io.netty.buffer.api.adaptor.BufferIntegratable;
import io.netty.buffer.api.adaptor.ByteBufAdaptor;
import io.netty.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty.buffer.api.internal.ResourceSupport;
import io.netty.buffer.api.ReadableComponent;
import io.netty.buffer.api.ReadableComponentProcessor;
import io.netty.buffer.api.WritableComponent;
import io.netty.buffer.api.WritableComponentProcessor;
import io.netty.buffer.api.internal.ArcDrop;
import io.netty.buffer.api.internal.Statics;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.PlatformDependent;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static io.netty.buffer.api.internal.Statics.bbslice;
import static io.netty.buffer.api.internal.Statics.bufferIsClosed;
import static io.netty.buffer.api.internal.Statics.bufferIsReadOnly;
import static io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;

class UnsafeBuffer extends ResourceSupport<Buffer, UnsafeBuffer> implements Buffer, ReadableComponent,
        WritableComponent, BufferIntegratable {
    private static final int CLOSED_SIZE = -1;
    private static final boolean ACCESS_UNALIGNED = PlatformDependent.isUnaligned();
    private UnsafeMemory memory; // The memory liveness; monitored by Cleaner.
    private Object base; // On-heap address reference object, or null for off-heap.
    private long baseOffset; // Offset of this buffer into the memory.
    private long address; // Resolved address (baseOffset + memory.address).
    private int rsize;
    private int wsize;
    private final AllocatorControl control;
    private ByteOrder order;
    private boolean flipBytes;
    private boolean readOnly;
    private int roff;
    private int woff;
    private boolean constBuffer;

    UnsafeBuffer(UnsafeMemory memory, long offset, int size, AllocatorControl allocatorControl,
                        Drop<UnsafeBuffer> drop) {
        super(new MakeInaccessibleOnDrop(ArcDrop.wrap(drop)));
        this.memory = memory;
        base = memory.base;
        baseOffset = offset;
        address = memory.address + offset;
        rsize = size;
        wsize = size;
        control = allocatorControl;
        order = ByteOrder.nativeOrder();
    }

    UnsafeBuffer(UnsafeBuffer parent) {
        super(new MakeInaccessibleOnDrop(new ArcDrop<>(ArcDrop.acquire(parent.unsafeGetDrop()))));
        control = parent.control;
        memory = parent.memory;
        base = parent.base;
        baseOffset = parent.baseOffset;
        address = parent.address;
        rsize = parent.rsize;
        wsize = parent.wsize;
        order = parent.order;
        flipBytes = parent.flipBytes;
        readOnly = parent.readOnly;
        roff = parent.roff;
        woff = parent.woff;
        constBuffer = true;
    }

    @Override
    public String toString() {
        return "Buffer[roff:" + roff + ", woff:" + woff + ", cap:" + rsize + ']';
    }

    @Override
    protected RuntimeException createResourceClosedException() {
        return bufferIsClosed(this);
    }

    @Override
    public Buffer order(ByteOrder order) {
        this.order = order;
        flipBytes = order != ByteOrder.nativeOrder();
        return this;
    }

    @Override
    public ByteOrder order() {
        return order;
    }

    @Override
    public int capacity() {
        return Math.max(0, rsize); // Use Math.max to make capacity of closed buffer equal to zero.
    }

    @Override
    public int readerOffset() {
        return roff;
    }

    @Override
    public Buffer readerOffset(int offset) {
        checkRead(offset, 0);
        roff = offset;
        return this;
    }

    @Override
    public int writerOffset() {
        return woff;
    }

    @Override
    public Buffer writerOffset(int offset) {
        checkWrite(offset, 0);
        woff = offset;
        return this;
    }

    @Override
    public Buffer fill(byte value) {
        checkSet(0, capacity());
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        try {
            PlatformDependent.setMemory(base, address, rsize, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public long nativeAddress() {
        return base == null? address : 0;
    }

    @Override
    public Buffer makeReadOnly() {
        readOnly = true;
        wsize = CLOSED_SIZE;
        return this;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public Buffer copy(int offset, int length) {
        checkGet(offset, length);
        int allocSize = Math.max(length, 1); // Allocators don't support allocating zero-sized buffers.
        AllocatorControl.UntetheredMemory memory = control.allocateUntethered(this, allocSize);
        UnsafeMemory unsafeMemory = memory.memory();
        Buffer copy = new UnsafeBuffer(unsafeMemory, 0, length, control, memory.drop());
        copyInto(offset, copy, 0, length);
        copy.writerOffset(length).order(order);
        if (readOnly) {
            copy = copy.makeReadOnly();
        }
        return copy;
    }

    @Override
    public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
        checkCopyIntoArgs(srcPos, length, destPos, dest.length);
        copyIntoArray(srcPos, dest, destPos, length);
    }

    private void copyIntoArray(int srcPos, byte[] dest, int destPos, int length) {
        long destOffset = PlatformDependent.byteArrayBaseOffset();
        try {
            PlatformDependent.copyMemory(base, address + srcPos, dest, destOffset + destPos, length);
        } finally {
            Reference.reachabilityFence(memory);
            Reference.reachabilityFence(dest);
        }
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        checkCopyIntoArgs(srcPos, length, destPos, dest.capacity());
        if (dest.hasArray()) {
            copyIntoArray(srcPos, dest.array(), dest.arrayOffset() + destPos, length);
        } else {
            assert dest.isDirect();
            long destAddr = PlatformDependent.directBufferAddress(dest);
            try {
                PlatformDependent.copyMemory(base, address + srcPos, null, destAddr + destPos, length);
            } finally {
                Reference.reachabilityFence(memory);
                Reference.reachabilityFence(dest);
            }
        }
    }

    private void checkCopyIntoArgs(int srcPos, int length, int destPos, int destLength) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        if (srcPos < 0) {
            throw new IllegalArgumentException("The srcPos cannot be negative: " + srcPos + '.');
        }
        if (length < 0) {
            throw new IllegalArgumentException("The length cannot be negative: " + length + '.');
        }
        if (rsize < srcPos + length) {
            throw new IllegalArgumentException("The srcPos + length is beyond the end of the buffer: " +
                    "srcPos = " + srcPos + ", length = " + length + '.');
        }
        if (destPos < 0) {
            throw new IllegalArgumentException("The destPos cannot be negative: " + destPos + '.');
        }
        if (destLength < destPos + length) {
            throw new IllegalArgumentException("The destPos + length is beyond the end of the destination: " +
                    "destPos = " + destPos + ", length = " + length + '.');
        }
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        checkCopyIntoArgs(srcPos, length, destPos, dest.capacity());
        if (dest.readOnly()) {
            throw bufferIsReadOnly(this);
        }
        long nativeAddress = dest.nativeAddress();
        try {
            if (nativeAddress != 0) {
                PlatformDependent.copyMemory(base, address + srcPos, null, nativeAddress + destPos, length);
            } else if (dest instanceof UnsafeBuffer) {
                UnsafeBuffer destUnsafe = (UnsafeBuffer) dest;
                PlatformDependent.copyMemory(
                        base, address + srcPos, destUnsafe.base, destUnsafe.address + destPos, length);
            } else {
                Statics.copyToViaReverseCursor(this, srcPos, dest, destPos, length);
            }
        } finally {
            Reference.reachabilityFence(memory);
            Reference.reachabilityFence(dest);
        }
    }

    @Override
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        if (fromOffset < 0) {
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        if (length < 0) {
            throw new IllegalArgumentException("The length cannot be negative: " + length + '.');
        }
        if (capacity() < fromOffset + length) {
            throw new IllegalArgumentException("The fromOffset + length is beyond the end of the buffer: " +
                    "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ByteCursor() {
            final UnsafeMemory memory = UnsafeBuffer.this.memory; // Keep memory alive.
            final Object baseObj = base;
            final long baseAddress = address;
            int index = fromOffset;
            final int end = index + length;
            long longValue = -1;
            byte byteValue = -1;

            @Override
            public boolean readLong() {
                if (index + Long.BYTES <= end) {
                    try {
                        long value = PlatformDependent.getLong(baseObj, baseAddress + index);
                        longValue = BIG_ENDIAN_NATIVE_ORDER? value : Long.reverseBytes(value);
                    } finally {
                        Reference.reachabilityFence(memory);
                    }
                    index += Long.BYTES;
                    return true;
                }
                return false;
            }

            @Override
            public long getLong() {
                return longValue;
            }

            @Override
            public boolean readByte() {
                if (index < end) {
                    try {
                        byteValue = PlatformDependent.getByte(baseObj, baseAddress + index);
                    } finally {
                        Reference.reachabilityFence(memory);
                    }
                    index++;
                    return true;
                }
                return false;
            }

            @Override
            public byte getByte() {
                return byteValue;
            }

            @Override
            public int currentOffset() {
                return index;
            }

            @Override
            public int bytesLeft() {
                return end - index;
            }
        };
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        if (fromOffset < 0) {
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        if (length < 0) {
            throw new IllegalArgumentException("The length cannot be negative: " + length + '.');
        }
        if (capacity() <= fromOffset) {
            throw new IllegalArgumentException("The fromOffset is beyond the end of the buffer: " + fromOffset + '.');
        }
        if (fromOffset - length < -1) {
            throw new IllegalArgumentException("The fromOffset - length would underflow the buffer: " +
                    "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ByteCursor() {
            final UnsafeMemory memory = UnsafeBuffer.this.memory; // Keep memory alive.
            final Object baseObj = base;
            final long baseAddress = address;
            int index = fromOffset;
            final int end = index - length;
            long longValue = -1;
            byte byteValue = -1;

            @Override
            public boolean readLong() {
                if (index - Long.BYTES >= end) {
                    index -= 7;
                    try {
                        long value = PlatformDependent.getLong(baseObj, baseAddress + index);
                        longValue = BIG_ENDIAN_NATIVE_ORDER? Long.reverseBytes(value) : value;
                    } finally {
                        Reference.reachabilityFence(memory);
                    }
                    index--;
                    return true;
                }
                return false;
            }

            @Override
            public long getLong() {
                return longValue;
            }

            @Override
            public boolean readByte() {
                if (index > end) {
                    try {
                        byteValue = PlatformDependent.getByte(baseObj, baseAddress + index);
                    } finally {
                        Reference.reachabilityFence(memory);
                    }
                    index--;
                    return true;
                }
                return false;
            }

            @Override
            public byte getByte() {
                return byteValue;
            }

            @Override
            public int currentOffset() {
                return index;
            }

            @Override
            public int bytesLeft() {
                return index - end;
            }
        };
    }

    @Override
    public void ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException(
                    "Buffer is not owned. Only owned buffers can call ensureWritable."));
        }
        if (size < 0) {
            throw new IllegalArgumentException("Cannot ensure writable for a negative size: " + size + '.');
        }
        if (minimumGrowth < 0) {
            throw new IllegalArgumentException("The minimum growth cannot be negative: " + minimumGrowth + '.');
        }
        if (rsize != wsize) {
            throw bufferIsReadOnly(this);
        }
        if (writableBytes() >= size) {
            // We already have enough space.
            return;
        }

        if (allowCompaction && writableBytes() + readerOffset() >= size) {
            // We can solve this with compaction.
            compact();
            return;
        }

        // Allocate a bigger buffer.
        long newSize = capacity() + (long) Math.max(size - writableBytes(), minimumGrowth);
        BufferAllocator.checkSize(newSize);
        var untethered = control.allocateUntethered(this, (int) newSize);
        UnsafeMemory memory = untethered.memory();

        // Copy contents.
        try {
            PlatformDependent.copyMemory(base, address, memory.base, memory.address, rsize);
        } finally {
            Reference.reachabilityFence(this.memory);
            Reference.reachabilityFence(memory);
        }

        // Release the old memory, and install the new memory:
        Drop<UnsafeBuffer> drop = untethered.drop();
        disconnectDrop(drop);
        attachNewMemory(memory, drop);
    }

    private Drop<UnsafeBuffer> disconnectDrop(Drop<UnsafeBuffer> newDrop) {
        var drop = (Drop<UnsafeBuffer>) unsafeGetDrop();
        int roff = this.roff;
        int woff = this.woff;
        drop.drop(this);
        unsafeSetDrop(new ArcDrop<>(newDrop));
        this.roff = roff;
        this.woff = woff;
        return drop;
    }

    private void attachNewMemory(UnsafeMemory memory, Drop<UnsafeBuffer> drop) {
        this.memory = memory;
        base = memory.base;
        baseOffset = 0;
        address = memory.address;
        rsize = memory.size;
        wsize = memory.size;
        constBuffer = false;
        drop.attach(this);
    }

    @Override
    public Buffer split(int splitOffset) {
        if (splitOffset < 0) {
            throw new IllegalArgumentException("The split offset cannot be negative: " + splitOffset + '.');
        }
        if (capacity() < splitOffset) {
            throw new IllegalArgumentException("The split offset cannot be greater than the buffer capacity, " +
                    "but the split offset was " + splitOffset + ", and capacity is " + capacity() + '.');
        }
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Cannot split a buffer that is not owned."));
        }
        var drop = (ArcDrop<UnsafeBuffer>) unsafeGetDrop();
        unsafeSetDrop(new ArcDrop<>(drop));
        // TODO maybe incrementing the existing ArcDrop is enough; maybe we don't need to wrap it in another ArcDrop.
        var splitBuffer = new UnsafeBuffer(memory, baseOffset, splitOffset, control, new ArcDrop<>(drop.increment()));
        splitBuffer.woff = Math.min(woff, splitOffset);
        splitBuffer.roff = Math.min(roff, splitOffset);
        splitBuffer.order(order());
        boolean readOnly = readOnly();
        if (readOnly) {
            splitBuffer.makeReadOnly();
        }
        // Split preserves const-state.
        splitBuffer.constBuffer = constBuffer;
        rsize -= splitOffset;
        baseOffset += splitOffset;
        address += splitOffset;
        if (!readOnly) {
            wsize = rsize;
        }
        woff = Math.max(woff, splitOffset) - splitOffset;
        roff = Math.max(roff, splitOffset) - splitOffset;
        return splitBuffer;
    }

    @Override
    public void compact() {
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Buffer must be owned in order to compact."));
        }
        if (readOnly()) {
            throw new BufferReadOnlyException("Buffer must be writable in order to compact, but was read-only.");
        }
        if (roff == 0) {
            return;
        }
        try {
            PlatformDependent.copyMemory(base, address + roff, base, address, woff - roff);
        } finally {
            Reference.reachabilityFence(memory);
        }
        woff -= roff;
        roff = 0;
    }

    @Override
    public int countComponents() {
        return 1;
    }

    @Override
    public int countReadableComponents() {
        return readableBytes() > 0? 1 : 0;
    }

    @Override
    public int countWritableComponents() {
        return writableBytes() > 0? 1 : 0;
    }

    // <editor-fold defaultstate="collapsed" desc="Readable/WritableComponent implementation.">
    @Override
    public boolean hasReadableArray() {
        return base instanceof byte[];
    }

    @Override
    public byte[] readableArray() {
        checkHasReadableArray();
        return (byte[]) base;
    }

    @Override
    public int readableArrayOffset() {
        checkHasReadableArray();
        return Math.toIntExact(address + roff - PlatformDependent.byteArrayBaseOffset());
    }

    private void checkHasReadableArray() {
        if (!hasReadableArray()) {
            throw new UnsupportedOperationException("No readable array available.");
        }
    }

    @Override
    public int readableArrayLength() {
        return woff - roff;
    }

    @Override
    public long readableNativeAddress() {
        return nativeAddress();
    }

    @Override
    public ByteBuffer readableBuffer() {
        final ByteBuffer buf;
        if (hasReadableArray()) {
            buf = bbslice(ByteBuffer.wrap(readableArray()), readableArrayOffset(), readableArrayLength());
        } else {
            buf = PlatformDependent.directBuffer(address + roff, readableBytes());
        }
        return buf.asReadOnlyBuffer().order(order());
    }

    @Override
    public boolean hasWritableArray() {
        return hasReadableArray();
    }

    @Override
    public byte[] writableArray() {
        checkHasWritableArray();
        return (byte[]) base;
    }

    @Override
    public int writableArrayOffset() {
        checkHasWritableArray();
        return Math.toIntExact(address + woff - PlatformDependent.byteArrayBaseOffset());
    }

    private void checkHasWritableArray() {
        if (!hasReadableArray()) {
            throw new UnsupportedOperationException("No writable array available.");
        }
    }

    @Override
    public int writableArrayLength() {
        return capacity() - woff;
    }

    @Override
    public long writableNativeAddress() {
        return nativeAddress();
    }

    @Override
    public ByteBuffer writableBuffer() {
        final ByteBuffer buf;
        if (hasWritableArray()) {
            buf = bbslice(ByteBuffer.wrap(writableArray()), writableArrayOffset(), writableArrayLength());
        } else {
            buf = PlatformDependent.directBuffer(address + woff, writableBytes());
        }
        return buf.order(order());
    }
    // </editor-fold>

    @Override
    public <E extends Exception> int forEachReadable(int initialIndex, ReadableComponentProcessor<E> processor)
            throws E {
        checkRead(readerOffset(), Math.max(1, readableBytes()));
        return processor.process(initialIndex, this)? 1 : -1;
    }

    @Override
    public <E extends Exception> int forEachWritable(int initialIndex, WritableComponentProcessor<E> processor)
            throws E {
        checkWrite(writerOffset(), Math.max(1, writableBytes()));
        return processor.process(initialIndex, this)? 1 : -1;
    }

    // <editor-fold defaultstate="collapsed" desc="Primitive accessors implementation.">
    @Override
    public byte readByte() {
        checkRead(roff, Byte.BYTES);
        try {
            var value = loadByte(address + roff);
            roff += Byte.BYTES;
            return value;
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public byte getByte(int roff) {
        checkGet(roff, Byte.BYTES);
        try {
            return loadByte(address + roff);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int readUnsignedByte() {
        return readByte() & 0xFF;
    }

    @Override
    public int getUnsignedByte(int roff) {
        return getByte(roff) & 0xFF;
    }

    @Override
    public Buffer writeByte(byte value) {
        checkWrite(woff, Byte.BYTES);
        long offset = address + woff;
        woff += Byte.BYTES;
        try {
            storeByte(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setByte(int woff, byte value) {
        checkSet(woff, Byte.BYTES);
        long offset = address + woff;
        try {
            storeByte(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedByte(int value) {
        checkWrite(woff, Byte.BYTES);
        long offset = address + woff;
        woff += Byte.BYTES;
        try {
            storeByte(offset, (byte) (value & 0xFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedByte(int woff, int value) {
        checkSet(woff, Byte.BYTES);
        long offset = address + woff;
        try {
            storeByte(offset, (byte) (value & 0xFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public char readChar() {
        checkRead(roff, Character.BYTES);
        try {
            long offset = address + roff;
            roff += Character.BYTES;
            return loadChar(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public char getChar(int roff) {
        checkGet(roff, Character.BYTES);
        try {
            long offset = address + roff;
            return loadChar(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeChar(char value) {
        checkWrite(woff, Character.BYTES);
        long offset = address + woff;
        woff += Character.BYTES;
        try {
            storeChar(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setChar(int woff, char value) {
        checkSet(woff, Character.BYTES);
        long offset = address + woff;
        try {
            storeChar(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public short readShort() {
        checkRead(roff, Short.BYTES);
        try {
            long offset = address + roff;
            roff += Short.BYTES;
            return loadShort(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public short getShort(int roff) {
        checkGet(roff, Short.BYTES);
        try {
            long offset = address + roff;
            return loadShort(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int getUnsignedShort(int roff) {
        return getShort(roff) & 0xFFFF;
    }

    @Override
    public Buffer writeShort(short value) {
        checkWrite(woff, Short.BYTES);
        long offset = address + woff;
        woff += Short.BYTES;
        try {
            storeShort(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setShort(int woff, short value) {
        checkSet(woff, Short.BYTES);
        long offset = address + woff;
        try {
            storeShort(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedShort(int value) {
        checkWrite(woff, Short.BYTES);
        long offset = address + woff;
        woff += Short.BYTES;
        try {
            storeShort(offset, (short) (value & 0xFFFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedShort(int woff, int value) {
        checkSet(woff, Short.BYTES);
        long offset = address + woff;
        try {
            storeShort(offset, (short) (value & 0xFFFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public int readMedium() {
        checkRead(roff, 3);
        long offset = address + roff;
        int value = order() == ByteOrder.BIG_ENDIAN ?
                loadByte(offset) << 16 |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) & 0xFF :
                loadByte(offset) & 0xFF |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) << 16;
        roff += 3;
        return value;
    }

    @Override
    public int getMedium(int roff) {
        checkGet(roff, 3);
        long offset = address + roff;
        return order() == ByteOrder.BIG_ENDIAN?
                loadByte(offset) << 16 |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) & 0xFF :
                loadByte(offset) & 0xFF |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) << 16;
    }

    @Override
    public int readUnsignedMedium() {
        checkRead(roff, 3);
        long offset = address + roff;
        int value = order() == ByteOrder.BIG_ENDIAN?
                (loadByte(offset) << 16 |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) & 0xFF) & 0xFFFFFF :
                (loadByte(offset) & 0xFF |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) << 16) & 0xFFFFFF;
        roff += 3;
        return value;
    }

    @Override
    public int getUnsignedMedium(int roff) {
        checkGet(roff, 3);
        long offset = address + roff;
        return order() == ByteOrder.BIG_ENDIAN?
                (loadByte(offset) << 16 |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) & 0xFF) & 0xFFFFFF :
                (loadByte(offset) & 0xFF |
                (loadByte(offset + 1) & 0xFF) << 8 |
                loadByte(offset + 2) << 16) & 0xFFFFFF;
    }

    @Override
    public Buffer writeMedium(int value) {
        checkWrite(woff, 3);
        long offset = address + woff;
        if (order() == ByteOrder.BIG_ENDIAN) {
            storeByte(offset, (byte) (value >> 16));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value & 0xFF));
        } else {
            storeByte(offset, (byte) (value & 0xFF));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value >> 16 & 0xFF));
        }
        woff += 3;
        return this;
    }

    @Override
    public Buffer setMedium(int woff, int value) {
        checkSet(woff, 3);
        long offset = address + woff;
        if (order() == ByteOrder.BIG_ENDIAN) {
            storeByte(offset, (byte) (value >> 16));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value & 0xFF));
        } else {
            storeByte(offset, (byte) (value & 0xFF));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value >> 16 & 0xFF));
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedMedium(int value) {
        checkWrite(woff, 3);
        long offset = address + woff;
        if (order() == ByteOrder.BIG_ENDIAN) {
            storeByte(offset, (byte) (value >> 16));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value & 0xFF));
        } else {
            storeByte(offset, (byte) (value & 0xFF));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value >> 16 & 0xFF));
        }
        woff += 3;
        return this;
    }

    @Override
    public Buffer setUnsignedMedium(int woff, int value) {
        checkSet(woff, 3);
        long offset = address + woff;
        if (order() == ByteOrder.BIG_ENDIAN) {
            storeByte(offset, (byte) (value >> 16));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value & 0xFF));
        } else {
            storeByte(offset, (byte) (value & 0xFF));
            storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
            storeByte(offset + 2, (byte) (value >> 16 & 0xFF));
        }
        return this;
    }

    @Override
    public int readInt() {
        checkRead(roff, Integer.BYTES);
        try {
            long offset = address + roff;
            roff += Integer.BYTES;
            return loadInt(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int getInt(int roff) {
        checkGet(roff, Integer.BYTES);
        try {
            long offset = address + roff;
            return loadInt(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0x0000_0000_FFFF_FFFFL;
    }

    @Override
    public long getUnsignedInt(int roff) {
        return getInt(roff) & 0x0000_0000_FFFF_FFFFL;
    }

    @Override
    public Buffer writeInt(int value) {
        checkWrite(woff, Integer.BYTES);
        long offset = address + woff;
        woff += Integer.BYTES;
        try {
            storeInt(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setInt(int woff, int value) {
        checkSet(woff, Integer.BYTES);
        long offset = address + woff;
        try {
            storeInt(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedInt(long value) {
        checkWrite(woff, Integer.BYTES);
        long offset = address + woff;
        woff += Integer.BYTES;
        try {
            storeInt(offset, (int) (value & 0xFFFF_FFFFL));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedInt(int woff, long value) {
        checkSet(woff, Integer.BYTES);
        long offset = address + woff;
        try {
            storeInt(offset, (int) (value & 0xFFFF_FFFFL));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public float readFloat() {
        checkRead(roff, Float.BYTES);
        try {
            long offset = address + roff;
            roff += Float.BYTES;
            return loadFloat(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public float getFloat(int roff) {
        checkGet(roff, Float.BYTES);
        try {
            long offset = address + roff;
            return loadFloat(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeFloat(float value) {
        checkWrite(woff, Float.BYTES);
        long offset = address + woff;
        woff += Float.BYTES;
        try {
            storeFloat(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setFloat(int woff, float value) {
        checkSet(woff, Float.BYTES);
        long offset = address + woff;
        try {
            storeFloat(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public long readLong() {
        checkRead(roff, Long.BYTES);
        try {
            long offset = address + roff;
            roff += Long.BYTES;
            return loadLong(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public long getLong(int roff) {
        checkGet(roff, Long.BYTES);
        try {
            long offset = address + roff;
            return loadLong(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeLong(long value) {
        checkWrite(woff, Long.BYTES);
        long offset = address + woff;
        woff += Long.BYTES;
        try {
            storeLong(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setLong(int woff, long value) {
        checkSet(woff, Long.BYTES);
        long offset = address + woff;
        try {
            storeLong(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public double readDouble() {
        checkRead(roff, Double.BYTES);
        try {
            long offset = address + roff;
            roff += Double.BYTES;
            return loadDouble(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public double getDouble(int roff) {
        checkGet(roff, Double.BYTES);
        try {
            long offset = address + roff;
            return loadDouble(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeDouble(double value) {
        checkWrite(woff, Double.BYTES);
        long offset = address + woff;
        woff += Double.BYTES;
        try {
            storeDouble(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setDouble(int woff, double value) {
        checkSet(woff, Double.BYTES);
        long offset = address + woff;
        try {
            storeDouble(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }
    // </editor-fold>

    @Override
    protected Owned<UnsafeBuffer> prepareSend() {
        var order = order();
        var roff = this.roff;
        var woff = this.woff;
        var readOnly = readOnly();
        var isConst = constBuffer;
        UnsafeMemory memory = this.memory;
        AllocatorControl control = this.control;
        long baseOffset = this.baseOffset;
        int rsize = this.rsize;
        makeInaccessible();
        return new Owned<UnsafeBuffer>() {
            @Override
            public UnsafeBuffer transferOwnership(Drop<UnsafeBuffer> drop) {
                UnsafeBuffer copy = new UnsafeBuffer(memory, baseOffset, rsize, control, drop);
                copy.order(order);
                copy.roff = roff;
                copy.woff = woff;
                if (readOnly) {
                    copy.makeReadOnly();
                }
                copy.constBuffer = isConst;
                return copy;
            }
        };
    }

    @Override
    protected Drop<UnsafeBuffer> unsafeGetDrop() {
        MakeInaccessibleOnDrop drop = (MakeInaccessibleOnDrop) super.unsafeGetDrop();
        return drop.delegate;
    }

    @Override
    protected void unsafeSetDrop(Drop<UnsafeBuffer> replacement) {
        super.unsafeSetDrop(new MakeInaccessibleOnDrop(replacement));
    }

    private static final class MakeInaccessibleOnDrop implements Drop<UnsafeBuffer> {
        final Drop<UnsafeBuffer> delegate;

        private MakeInaccessibleOnDrop(Drop<UnsafeBuffer> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void drop(UnsafeBuffer buf) {
            try {
                delegate.drop(buf);
            } finally {
                buf.makeInaccessible();
            }
        }

        @Override
        public void attach(UnsafeBuffer buf) {
            delegate.attach(buf);
        }

        @Override
        public String toString() {
            return "MakeInaccessibleOnDrop(" + delegate + ')';
        }
    }

    void makeInaccessible() {
        roff = 0;
        woff = 0;
        rsize = CLOSED_SIZE;
        wsize = CLOSED_SIZE;
        readOnly = false;
    }

    @Override
    public boolean isOwned() {
        return super.isOwned() && ((ArcDrop<UnsafeBuffer>) unsafeGetDrop()).isOwned();
    }

    @Override
    public int countBorrows() {
        return super.countBorrows() + ((ArcDrop<UnsafeBuffer>) unsafeGetDrop()).countBorrows();
    }

    private void checkRead(int index, int size) {
        if (index < 0 || woff < index + size) {
            throw readAccessCheckException(index);
        }
    }

    private void checkGet(int index, int size) {
        if (index < 0 || rsize < index + size) {
            throw readAccessCheckException(index);
        }
    }

    private void checkWrite(int index, int size) {
        if (index < roff || wsize < index + size) {
            throw writeAccessCheckException(index);
        }
    }

    private void checkSet(int index, int size) {
        if (index < 0 || wsize < index + size) {
            throw writeAccessCheckException(index);
        }
    }

    private RuntimeException readAccessCheckException(int index) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        return outOfBounds(index);
    }

    private RuntimeException writeAccessCheckException(int index) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed(this);
        }
        if (wsize != rsize) {
            return bufferIsReadOnly(this);
        }
        return outOfBounds(index);
    }

    private IndexOutOfBoundsException outOfBounds(int index) {
        return new IndexOutOfBoundsException(
                "Index " + index + " is out of bounds: [read 0 to " + woff + ", write 0 to " +
                        rsize + "].");
    }

    private byte loadByte(long off) {
        return PlatformDependent.getByte(base, off);
    }

    private char loadChar(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getChar(base, offset);
            return flipBytes? Character.reverseBytes(value) : value;
        }
        return loadCharUnaligned(offset);
    }

    private char loadCharUnaligned(long offset) {
        final char value;
        Object b = base;
        if ((offset & 1) == 0) {
            value = PlatformDependent.getChar(b, offset);
        } else {
            value = (char) (PlatformDependent.getByte(b, offset) << 8 |
                    PlatformDependent.getByte(b, offset + 1));
        }
        return flipBytes? Character.reverseBytes(value) : value;
    }

    private short loadShort(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getShort(base, offset);
            return flipBytes? Short.reverseBytes(value) : value;
        }
        return loadShortUnaligned(offset);
    }

    private short loadShortUnaligned(long offset) {
        final short value;
        Object b = base;
        if ((offset & 1) == 0) {
            value = PlatformDependent.getShort(b, offset);
        } else {
            value = (short) (PlatformDependent.getByte(b, offset) << 8 |
                    PlatformDependent.getByte(b, offset + 1));
        }
        return flipBytes? Short.reverseBytes(value) : value;
    }

    private int loadInt(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getInt(base, offset);
            return flipBytes? Integer.reverseBytes(value) : value;
        }
        return loadIntUnaligned(offset);
    }

    private int loadIntUnaligned(long offset) {
        final int value;
        Object b = base;
        if ((offset & 3) == 0) {
            value = PlatformDependent.getInt(b, offset);
        } else if ((offset & 1) == 0) {
            value = PlatformDependent.getShort(b, offset) << 16 |
                    PlatformDependent.getShort(b, offset + 2);
        } else {
            value = PlatformDependent.getByte(b, offset) << 24 |
                    PlatformDependent.getByte(b, offset + 1) << 16 |
                    PlatformDependent.getByte(b, offset + 2) << 8 |
                    PlatformDependent.getByte(b, offset + 3);
        }
        return flipBytes? Integer.reverseBytes(value) : value;
    }

    private float loadFloat(long offset) {
        if (ACCESS_UNALIGNED) {
            if (flipBytes) {
                var value = PlatformDependent.getInt(base, offset);
                return Float.intBitsToFloat(Integer.reverseBytes(value));
            }
            return PlatformDependent.getFloat(base, offset);
        }
        return loadFloatUnaligned(offset);
    }

    private float loadFloatUnaligned(long offset) {
        return Float.intBitsToFloat(loadIntUnaligned(offset));
    }

    private long loadLong(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getLong(base, offset);
            return flipBytes? Long.reverseBytes(value) : value;
        }
        return loadLongUnaligned(offset);
    }

    private long loadLongUnaligned(long offset) {
        final long value;
        Object b = base;
        if ((offset & 7) == 0) {
            value = PlatformDependent.getLong(b, offset);
        } else if ((offset & 3) == 0) {
            value = (long) PlatformDependent.getInt(b, offset) << 32 |
                    PlatformDependent.getInt(b, offset + 4);
        } else if ((offset & 1) == 0) {
            value = (long) PlatformDependent.getShort(b, offset) << 48 |
                    (long) PlatformDependent.getShort(b, offset + 2) << 32 |
                    (long) PlatformDependent.getShort(b, offset + 4) << 16 |
                    PlatformDependent.getShort(b, offset + 6);
        } else {
            value = (long) PlatformDependent.getByte(b, offset) << 54 |
                    (long) PlatformDependent.getByte(b, offset + 1) << 48 |
                    (long) PlatformDependent.getByte(b, offset + 2) << 40 |
                    (long) PlatformDependent.getByte(b, offset + 3) << 32 |
                    (long) PlatformDependent.getByte(b, offset + 4) << 24 |
                    (long) PlatformDependent.getByte(b, offset + 5) << 16 |
                    (long) PlatformDependent.getByte(b, offset + 6) << 8 |
                    PlatformDependent.getByte(b, offset + 7);
        }
        return flipBytes? Long.reverseBytes(value) : value;
    }

    private double loadDouble(long offset) {
        if (ACCESS_UNALIGNED) {
            if (flipBytes) {
                var value = PlatformDependent.getLong(base, offset);
                return Double.longBitsToDouble(Long.reverseBytes(value));
            }
            return PlatformDependent.getDouble(base, offset);
        }
        return loadDoubleUnaligned(offset);
    }

    private double loadDoubleUnaligned(long offset) {
        return Double.longBitsToDouble(loadLongUnaligned(offset));
    }

    private void storeByte(long offset, byte value) {
        PlatformDependent.putByte(base, offset, value);
    }

    private void storeChar(long offset, char value) {
        if (flipBytes) {
            value = Character.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putChar(base, offset, value);
        } else {
            storeCharUnaligned(offset, value);
        }
    }

    private void storeCharUnaligned(long offset, char value) {
        Object b = base;
        if ((offset & 1) == 0) {
            PlatformDependent.putChar(b, offset, value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 1, (byte) value);
        }
    }

    private void storeShort(long offset, short value) {
        if (flipBytes) {
            value = Short.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putShort(base, offset, value);
        } else {
            storeShortUnaligned(offset, value);
        }
    }

    private void storeShortUnaligned(long offset, short value) {
        Object b = base;
        if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 1, (byte) value);
        }
    }

    private void storeInt(long offset, int value) {
        if (flipBytes) {
            value = Integer.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putInt(base, offset, value);
        } else {
            storeIntUnaligned(offset, value);
        }
    }

    private void storeIntUnaligned(long offset, int value) {
        Object b = base;
        if ((offset & 3) == 0) {
            PlatformDependent.putInt(b, offset, value);
        } else if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, (short) (value >> 16));
            PlatformDependent.putShort(b, offset + 2, (short) value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 24));
            PlatformDependent.putByte(b, offset + 1, (byte) (value >> 16));
            PlatformDependent.putByte(b, offset + 2, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 3, (byte) value);
        }
    }

    private void storeFloat(long offset, float value) {
        storeInt(offset, Float.floatToRawIntBits(value));
    }

    private void storeLong(long offset, long value) {
        if (flipBytes) {
            value = Long.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putLong(base, offset, value);
        } else {
            storeLongUnaligned(offset, value);
        }
    }

    private void storeLongUnaligned(long offset, long value) {
        Object b = base;
        if ((offset & 7) == 0) {
            PlatformDependent.putLong(b, offset, value);
        } else if ((offset & 3) == 0) {
            PlatformDependent.putInt(b, offset, (int) (value >> 32));
            PlatformDependent.putInt(b, offset + 4, (int) value);
        } else if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, (short) (value >> 48));
            PlatformDependent.putShort(b, offset + 16, (short) (value >> 32));
            PlatformDependent.putShort(b, offset + 32, (short) (value >> 16));
            PlatformDependent.putShort(b, offset + 48, (short) value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 56));
            PlatformDependent.putByte(b, offset + 1, (byte) (value >> 48));
            PlatformDependent.putByte(b, offset + 2, (byte) (value >> 40));
            PlatformDependent.putByte(b, offset + 3, (byte) (value >> 32));
            PlatformDependent.putByte(b, offset + 4, (byte) (value >> 24));
            PlatformDependent.putByte(b, offset + 5, (byte) (value >> 16));
            PlatformDependent.putByte(b, offset + 6, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 7, (byte) value);
        }
    }

    private void storeDouble(long offset, double value) {
        storeLong(offset, Double.doubleToRawLongBits(value));
    }

    Object recover() {
        return memory;
    }

    // <editor-fold name="BufferIntegratable methods">
    private ByteBufAdaptor adaptor;
    @Override
    public ByteBuf asByteBuf() {
        ByteBufAdaptor bba = adaptor;
        if (bba == null) {
            ByteBufAllocatorAdaptor alloc = new ByteBufAllocatorAdaptor(
                    BufferAllocator.heap(), BufferAllocator.direct());
            return adaptor = new ByteBufAdaptor(alloc, this);
        }
        return bba;
    }

    @Override
    public int refCnt() {
        return isAccessible()? 1 + countBorrows() : 0;
    }

    @Override
    public ReferenceCounted retain() {
        return retain(1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        for (int i = 0; i < increment; i++) {
            acquire();
        }
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        return this;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return release(1);
    }

    @Override
    public boolean release(int decrement) {
        int refCount = 1 + countBorrows();
        if (!isAccessible() || decrement > refCount) {
            throw new IllegalReferenceCountException(refCount, -decrement);
        }
        for (int i = 0; i < decrement; i++) {
            try {
                close();
            } catch (RuntimeException e) {
                throw new IllegalReferenceCountException(e);
            }
        }
        return !isAccessible();
    }
    // </editor-fold>
}
