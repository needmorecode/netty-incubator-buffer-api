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
package io.netty.buffer.api.tests.examples.bytetomessagedecoder;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.CompositeBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelHandler} which decodes bytes in a stream-like fashion from one {@link Buffer} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link Buffer}, creates a new {@link Buffer} and forward it to the
 * next {@link ChannelHandler} in the {@link ChannelPipeline}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link Buffer} in)
 *                 throws {@link Exception} {
 *             ctx.fireChannelRead(in.split());
 *         }
 *     }
 * </pre>
 *
 * <h3>Frame detection</h3>
 * <p>
 * Generally frame detection should be handled earlier in the pipeline by adding a
 * {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder}, {@link LengthFieldBasedFrameDecoder},
 * or {@link LineBasedFrameDecoder}.
 * <p>
 * If a custom frame decoder is required, then one needs to be careful when implementing
 * one with {@link ByteToMessageDecoder}. Ensure there are enough bytes in the buffer for a
 * complete frame by checking {@link Buffer#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like
 * {@link Buffer#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like
 * {@link Buffer#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 * <p>
 * Some methods such as {@link Buffer#split(int)} will cause a memory leak if the returned buffer
 * is not released or fired through the {@link ChannelPipeline} via
 * {@link ChannelHandlerContext#fireChannelRead(Object)}.
 */
public abstract class ByteToMessageDecoder extends ChannelHandlerAdapter {

    /**
     * Cumulate {@link Buffer}s by merge them into one {@link Buffer}'s, using memory copies.
     */
    public static final Cumulator MERGE_CUMULATOR = (alloc, cumulation, in) -> {
        if (cumulation.readableBytes() == 0 && !CompositeBuffer.isComposite(cumulation)) {
            // If cumulation is empty and input buffer is contiguous, use it directly
            cumulation.close();
            return in;
        }
        // We must release 'in' in all cases as otherwise it may produce a leak if writeBytes(...) throw
        // for whatever release (for example because of OutOfMemoryError)
        try (in) {
            final int required = in.readableBytes();
            if (required > cumulation.writableBytes() || cumulation.readOnly()) {
                // Expand cumulation (by replacing it) under the following conditions:
                // - cumulation cannot be resized to accommodate the additional data
                // - cumulation can be expanded with a reallocation operation to accommodate but the buffer is
                //   assumed to be shared (e.g. refCnt() > 1) and the reallocation may not be safe.
                return expandCumulation(alloc, cumulation, in);
            }
            cumulation.writeBytes(in);
            return cumulation;
        }
    };

    /**
     * Cumulate {@link Buffer}s by add them to a composite buffer and so do no memory copy whenever
     * possible.
     * Be aware that composite buffer use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower then just use the {@link #MERGE_CUMULATOR}.
     */
    public static final Cumulator COMPOSITE_CUMULATOR = (alloc, cumulation, in) -> {
        if (cumulation.readableBytes() == 0) {
            cumulation.close();
            return in;
        }
        Buffer composite;
        try (in) {
            if (CompositeBuffer.isComposite(cumulation)) {
                composite = cumulation;
                if (composite.writerOffset() != composite.capacity()) {
                    // Writer index must equal capacity if we are going to "write"
                    // new components to the end.
                    composite = cumulation.split(composite.writerOffset());
                    cumulation.close();
                }
            } else {
                composite = CompositeBuffer.compose(alloc, cumulation.send());
            }
            ((CompositeBuffer) composite).extendWith(in.send());
            return composite;
        }
    };

    Buffer cumulation;
    private Cumulator cumulator = MERGE_CUMULATOR;
    private boolean singleDecode;
    private boolean first;

    /**
     * This flag is used to determine if we need to call {@link ChannelHandlerContext#read()} to consume more data
     * when {@link ChannelConfig#isAutoRead()} is {@code false}.
     */
    private boolean firedChannelRead;

    private int discardAfterReads = 16;
    private int numReads;
    private ByteToMessageDecoderContext context;

    protected ByteToMessageDecoder() {
        ensureNotSharable();
    }

    /**
     * If set then only one message is decoded on each {@link #channelRead(ChannelHandlerContext, Object)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #channelRead(ChannelHandlerContext, Object)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * Set the {@link Cumulator} to use for cumulate the received {@link Buffer}s.
     */
    public void setCumulator(Cumulator cumulator) {
        requireNonNull(cumulator, "cumulator");
        this.cumulator = cumulator;
    }

    /**
     * Set the number of reads after which {@link Buffer#compact()} is called to free up memory.
     * The default is {@code 16}.
     */
    public void setDiscardAfterReads(int discardAfterReads) {
        checkPositive(discardAfterReads, "discardAfterReads");
        this.discardAfterReads = discardAfterReads;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected Buffer internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return newEmptyBuffer();
        }
    }

    private static Buffer newEmptyBuffer() {
        return CompositeBuffer.compose(BufferAllocator.onHeapUnpooled());
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        context = new ByteToMessageDecoderContext(ctx);
        handlerAdded0(context);
    }

    protected void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Buffer buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;
            numReads = 0;
            int readable = buf.readableBytes();
            if (readable > 0) {
                ctx.fireChannelRead(buf);
                ctx.fireChannelReadComplete();
            } else {
                buf.close();
            }
        }
        handlerRemoved0(context);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Buffer) {
            try {
                Buffer data = (Buffer) msg;
                first = cumulation == null;
                if (first) {
                    cumulation = data;
                } else {
//                    ByteBufAllocator alloc = ctx.alloc(); // TODO this API integration needs more work
                    BufferAllocator alloc = BufferAllocator.onHeapUnpooled();
                    cumulation = cumulator.cumulate(alloc, cumulation, data);
                }
                assert context.ctx == ctx || ctx == context;

                callDecode(context, cumulation); // TODO we'll want to split here, and simplify lifetime handling
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                if (cumulation != null && cumulation.readableBytes() == 0) {
                    numReads = 0;
                    cumulation.close();
                    cumulation = null;
                } else if (++ numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    discardSomeReadBytes(); // TODO no need to so this dance because ensureWritable can compact for us
                }

                firedChannelRead |= context.fireChannelReadCallCount() > 0;
                context.reset();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        numReads = 0;
        discardSomeReadBytes();
        if (!firedChannelRead && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        firedChannelRead = false;
        ctx.fireChannelReadComplete();
    }

    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first) {
            // Discard some bytes if possible to make more room in the buffer.
            cumulation.compact();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        assert context.ctx == ctx || ctx == context;
        channelInputClosed(context, true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
        if (evt instanceof ChannelInputShutdownEvent) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            assert context.ctx == ctx || ctx == context;
            channelInputClosed(context, false);
        }
    }

    private void channelInputClosed(ByteToMessageDecoderContext ctx, boolean callChannelInactive) {
        try {
            channelInputClosed(ctx);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            if (cumulation != null) {
                cumulation.close();
                cumulation = null;
            }
            if (ctx.fireChannelReadCallCount() > 0) {
                ctx.reset();
                // Something was read, call fireChannelReadComplete()
                ctx.fireChannelReadComplete();
            }
            if (callChannelInactive) {
                ctx.fireChannelInactive();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * {@link ChannelInputShutdownEvent}.
     */
    void channelInputClosed(ByteToMessageDecoderContext ctx) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation);
            // If callDecode(...) removed the handle from the pipeline we should not call decodeLast(...) as this would
            // be unexpected.
            if (!ctx.isRemoved()) {
                // Use Unpooled.EMPTY_BUFFER if cumulation become null after calling callDecode(...).
                // See https://github.com/netty/netty/issues/10802.
                Buffer buffer = cumulation == null ? newEmptyBuffer() : cumulation;
                decodeLast(ctx, buffer);
            }
        } else {
            decodeLast(ctx, newEmptyBuffer());
        }
    }

    /**
     * Called once data should be decoded from the given {@link Buffer}. This method will call
     * {@link #decode(ChannelHandlerContext, Buffer)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link Buffer} from which to read data
     */
    void callDecode(ByteToMessageDecoderContext ctx, Buffer in) {
        try {
            while (in.readableBytes() > 0 && !ctx.isRemoved()) {

                int oldInputLength = in.readableBytes();
                int numReadCalled = ctx.fireChannelReadCallCount();
                decodeRemovalReentryProtection(ctx, in);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (numReadCalled == ctx.fireChannelReadCallCount()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }

                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }

    /**
     * Decode the from one {@link Buffer} to an other. This method will be called till either the input
     * {@link Buffer} has nothing to read when return from this method or till nothing was read from the input
     * {@link Buffer}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link Buffer} from which to read data
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, Buffer in) throws Exception;

    /**
     * Decode the from one {@link Buffer} to an other. This method will be called till either the input
     * {@link Buffer} has nothing to read when return from this method or till nothing was read from the input
     * {@link Buffer}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link Buffer} from which to read data
     * @throws Exception    is thrown if an error occurs
     */
    final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, Buffer in)
            throws Exception {
        decode(ctx, in);
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, Buffer)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, Buffer in) throws Exception {
        if (in.readableBytes() > 0) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in);
        }
    }

    private static Buffer expandCumulation(BufferAllocator alloc, Buffer oldCumulation, Buffer in) {
        int newSize = MathUtil.safeFindNextPositivePowerOfTwo(oldCumulation.readableBytes() + in.readableBytes());
        Buffer newCumulation = alloc.allocate(newSize);
        Buffer toRelease = newCumulation;
        try {
            newCumulation.writeBytes(oldCumulation);
            newCumulation.writeBytes(in);
            toRelease = oldCumulation;
            return newCumulation;
        } finally {
            toRelease.close();
        }
    }

    /**
     * Cumulate {@link Buffer}s.
     */
    public interface Cumulator {
        /**
         * Cumulate the given {@link Buffer}s and return the {@link Buffer} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link Buffer}s and so
         * call {@link Buffer#close()} if a {@link Buffer} is fully consumed.
         */
        Buffer cumulate(BufferAllocator alloc, Buffer cumulation, Buffer in);
    }

    // Package private so we can also make use of it in ReplayingDecoder.
    static final class ByteToMessageDecoderContext implements ChannelHandlerContext {
        private final ChannelHandlerContext ctx;
        private int fireChannelReadCalled;

        private ByteToMessageDecoderContext(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        void reset() {
            fireChannelReadCalled = 0;
        }

        int fireChannelReadCallCount() {
            return fireChannelReadCalled;
        }

        @Override
        public Channel channel() {
            return ctx.channel();
        }

        @Override
        public EventExecutor executor() {
            return ctx.executor();
        }

        @Override
        public String name() {
            return ctx.name();
        }

        @Override
        public ChannelHandler handler() {
            return ctx.handler();
        }

        @Override
        public boolean isRemoved() {
            return ctx.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            ctx.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            ctx.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            ctx.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            ctx.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            ctx.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            ctx.fireUserEventTriggered(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            fireChannelReadCalled ++;
            ctx.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            ctx.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            ctx.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelHandlerContext read() {
            ctx.read();
            return this;
        }

        @Override
        public Future<Void> write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelHandlerContext flush() {
            ctx.flush();
            return this;
        }

        @Override
        public ChannelPipeline pipeline() {
            return ctx.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return ctx.alloc();
        }

        @Override
        @Deprecated
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return ctx.attr(key);
        }

        @Override
        @Deprecated
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return ctx.hasAttr(key);
        }

        @Override
        public Future<Void> bind(SocketAddress localAddress) {
            return ctx.bind(localAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress) {
            return ctx.connect(remoteAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return ctx.connect(remoteAddress, localAddress);
        }

        @Override
        public Future<Void> disconnect() {
            return ctx.disconnect();
        }

        @Override
        public Future<Void> close() {
            return ctx.close();
        }

        @Override
        public Future<Void> deregister() {
            return ctx.deregister();
        }

        @Override
        public Future<Void> register() {
            return ctx.register();
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
            return ctx.writeAndFlush(msg);
        }

        @Override
        public Promise<Void> newPromise() {
            return ctx.newPromise();
        }

        @Override
        public Future<Void> newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public Future<Void> newFailedFuture(Throwable cause) {
            return ctx.newFailedFuture(cause);
        }
    }
}
