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
package io.netty.buffer.b2;

/**
 * This interface is just the primitive data accessor methods that {@link Buf} exposes.
 * It can be useful if you only need the data access methods, and perhaps wish to decorate or modify their behaviour.
 * Usually, you'd use the {@link Buf} interface directly, since this lets you properly control the buffer reference count.
 */
public interface BufAccessors {
    // ### CODEGEN START primitive accessors interface
    // <editor-fold defaultstate="collapsed" desc="Generated primitive accessors interface.">

    /**
     * Get the byte value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Byte#BYTES}.
     * The value is read using a two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The byte value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Byte#BYTES}.
     */
    byte readByte();

    /**
     * Get the byte value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The byte value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Byte#BYTES}.
     */
    byte readByte(int roff);

    /**
     * Get the unsigned byte value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Byte#BYTES}.
     * The value is read using an unsigned two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The unsigned byte value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Byte#BYTES}.
     */
    int readUnsignedByte();

    /**
     * Get the unsigned byte value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using an unsigned two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The unsigned byte value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Byte#BYTES}.
     */
    int readUnsignedByte(int roff);

    /**
     * Set the given byte value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Byte#BYTES}.
     * The value is written using a two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The byte value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Byte#BYTES}.
     */
    Buf writeByte(byte value);

    /**
     * Set the given byte value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The byte value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Byte#BYTES}.
     */
    Buf writeByte(int woff, byte value);

    /**
     * Set the given unsigned byte value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Byte#BYTES}.
     * The value is written using an unsigned two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Byte#BYTES}.
     */
    Buf writeUnsignedByte(int value);

    /**
     * Set the given unsigned byte value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using an unsigned two's complement 8-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Byte#BYTES}.
     */
    Buf writeUnsignedByte(int woff, int value);

    /**
     * Get the char value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by 2.
     * The value is read using a 2-byte UTF-16 encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The char value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than 2.
     */
    char readChar();

    /**
     * Get the char value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a 2-byte UTF-16 encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The char value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 2.
     */
    char readChar(int roff);

    /**
     * Set the given char value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by 2.
     * The value is written using a 2-byte UTF-16 encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The char value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than 2.
     */
    Buf writeChar(char value);

    /**
     * Set the given char value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a 2-byte UTF-16 encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The char value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 2.
     */
    Buf writeChar(int woff, char value);

    /**
     * Get the short value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Short#BYTES}.
     * The value is read using a two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The short value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Short#BYTES}.
     */
    short readShort();

    /**
     * Get the short value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The short value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Short#BYTES}.
     */
    short readShort(int roff);

    /**
     * Get the unsigned short value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Short#BYTES}.
     * The value is read using an unsigned two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The unsigned short value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Short#BYTES}.
     */
    int readUnsignedShort();

    /**
     * Get the unsigned short value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using an unsigned two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The unsigned short value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Short#BYTES}.
     */
    int readUnsignedShort(int roff);

    /**
     * Set the given short value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Short#BYTES}.
     * The value is written using a two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The short value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Short#BYTES}.
     */
    Buf writeShort(short value);

    /**
     * Set the given short value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The short value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Short#BYTES}.
     */
    Buf writeShort(int woff, short value);

    /**
     * Set the given unsigned short value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Short#BYTES}.
     * The value is written using an unsigned two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Short#BYTES}.
     */
    Buf writeUnsignedShort(int value);

    /**
     * Set the given unsigned short value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using an unsigned two's complement 16-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Short#BYTES}.
     */
    Buf writeUnsignedShort(int woff, int value);

    /**
     * Get the int value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by 3.
     * The value is read using a two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than 3.
     */
    int readMedium();

    /**
     * Get the int value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The int value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 3.
     */
    int readMedium(int roff);

    /**
     * Get the unsigned int value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by 3.
     * The value is read using an unsigned two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The unsigned int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than 3.
     */
    int readUnsignedMedium();

    /**
     * Get the unsigned int value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using an unsigned two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The unsigned int value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 3.
     */
    int readUnsignedMedium(int roff);

    /**
     * Set the given int value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by 3.
     * The value is written using a two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than 3.
     */
    Buf writeMedium(int value);

    /**
     * Set the given int value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 3.
     */
    Buf writeMedium(int woff, int value);

    /**
     * Set the given unsigned int value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by 3.
     * The value is written using an unsigned two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than 3.
     */
    Buf writeUnsignedMedium(int value);

    /**
     * Set the given unsigned int value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using an unsigned two's complement 24-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus 3.
     */
    Buf writeUnsignedMedium(int woff, int value);

    /**
     * Get the int value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Integer#BYTES}.
     * The value is read using a two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Integer#BYTES}.
     */
    int readInt();

    /**
     * Get the int value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The int value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Integer#BYTES}.
     */
    int readInt(int roff);

    /**
     * Get the unsigned int value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Integer#BYTES}.
     * The value is read using an unsigned two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The unsigned int value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Integer#BYTES}.
     */
    long readUnsignedInt();

    /**
     * Get the unsigned int value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using an unsigned two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The unsigned int value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Integer#BYTES}.
     */
    long readUnsignedInt(int roff);

    /**
     * Set the given int value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Integer#BYTES}.
     * The value is written using a two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Integer#BYTES}.
     */
    Buf writeInt(int value);

    /**
     * Set the given int value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The int value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Integer#BYTES}.
     */
    Buf writeInt(int woff, int value);

    /**
     * Set the given unsigned int value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Integer#BYTES}.
     * The value is written using an unsigned two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The long value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Integer#BYTES}.
     */
    Buf writeUnsignedInt(long value);

    /**
     * Set the given unsigned int value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using an unsigned two's complement 32-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The long value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Integer#BYTES}.
     */
    Buf writeUnsignedInt(int woff, long value);

    /**
     * Get the float value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Float#BYTES}.
     * The value is read using a 32-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The float value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Float#BYTES}.
     */
    float readFloat();

    /**
     * Get the float value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a 32-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The float value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Float#BYTES}.
     */
    float readFloat(int roff);

    /**
     * Set the given float value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Float#BYTES}.
     * The value is written using a 32-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The float value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Float#BYTES}.
     */
    Buf writeFloat(float value);

    /**
     * Set the given float value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a 32-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The float value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Float#BYTES}.
     */
    Buf writeFloat(int woff, float value);

    /**
     * Get the long value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Long#BYTES}.
     * The value is read using a two's complement 64-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The long value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Long#BYTES}.
     */
    long readLong();

    /**
     * Get the long value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a two's complement 64-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The long value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Long#BYTES}.
     */
    long readLong(int roff);

    /**
     * Set the given long value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Long#BYTES}.
     * The value is written using a two's complement 64-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The long value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Long#BYTES}.
     */
    Buf writeLong(long value);

    /**
     * Set the given long value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a two's complement 64-bit encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The long value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Long#BYTES}.
     */
    Buf writeLong(int woff, long value);

    /**
     * Get the double value at the current {@link Buf#readerIndex()},
     * and increases the reader offset by {@link Double#BYTES}.
     * The value is read using a 64-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @return The double value at the current reader offset.
     * @throws IndexOutOfBoundsException If {@link Buf#readableBytes} is less than {@link Double#BYTES}.
     */
    double readDouble();

    /**
     * Get the double value at the given reader offset.
     * The {@link Buf#readerIndex()} is not modified.
     * The value is read using a 64-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param roff The read offset, an absolute index into this buffer, to read from.
     * @return The double value at the given offset.
     * @throws IndexOutOfBoundsException if the given index is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Double#BYTES}.
     */
    double readDouble(int roff);

    /**
     * Set the given double value at the current {@link Buf#writerIndex()},
     * and increase the writer offset by {@link Double#BYTES}.
     * The value is written using a 64-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param value The double value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException If {@link Buf#writableBytes} is less than {@link Double#BYTES}.
     */
    Buf writeDouble(double value);

    /**
     * Set the given double value at the given write offset. The {@link Buf#writerIndex()} is not modified.
     * The value is written using a 64-bit IEEE floating point encoding,
     * with the {@link Buf#order() configured} default byte order.
     *
     * @param woff The write offset, an absolute index into this buffer to write to.
     * @param value The double value to write.
     * @return This Buf.
     * @throws IndexOutOfBoundsException if the given offset is out of bounds of the buffer, that is, less than 0 or
     *                                   greater than or equal to {@link Buf#capacity()} minus {@link Double#BYTES}.
     */
    Buf writeDouble(int woff, double value);
    // </editor-fold>
    // ### CODEGEN END primitive accessors interface
}