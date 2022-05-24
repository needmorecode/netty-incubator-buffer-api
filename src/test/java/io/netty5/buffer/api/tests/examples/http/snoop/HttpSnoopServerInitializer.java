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
package io.netty5.buffer.api.tests.examples.http.snoop;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.ssl.SslContext;

public class HttpSnoopServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final BufferAllocator allocator;

    public HttpSnoopServerInitializer(SslContext sslCtx, BufferAllocator allocator) {
        this.sslCtx = sslCtx;
        this.allocator = allocator;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(allocator));
        }
        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new HttpSnoopServerHandler());
    }
}
