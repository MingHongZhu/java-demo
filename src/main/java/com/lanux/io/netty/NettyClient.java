package com.lanux.io.netty;

import com.lanux.io.NetConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lanux on 2017/9/16.
 */
public class NettyClient {
    //nThreads=0: Netty 会首先从系统属性中获取 "io.netty.eventLoopThreads" 的值, 如果我们没有设置它的话, 那么就返回默认值: 处理器核心数 * 2.
    EventLoopGroup workerGroup = new NioEventLoopGroup(0,
            new ThreadFactory() {
                AtomicInteger count = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "NioEventLoop-" + count.getAndIncrement());
                }
            });

    final Bootstrap b = new Bootstrap();

    public NettyClient() throws Exception {
        try {
            // 因为client不需要监听SelectionKey.OP_ACCEPT,只需要监听SelectionKey.OP_READ，所以只需要workerGroup
            b.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, NetConfig.SO_TIMEOUT)
                    //Each channel has its own pipeline and it is created automatically when a new channel is created.
                    .handler(new ChildChannelHandler());
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public static class ChildChannelHandler extends
            ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS))
                    .addLast(new NettyMessageDecoder());
//                    .addLast(new NettyMessageEncoder())
//                    .addLast(new LoginAuthReqHandler());
        }

    }

    public Channel connect(String host, int port) {
        Channel channel;
        ChannelFuture connectFuture = b.connect(host, port);
        System.out.println("Netty time Client connected at port " + port);
        try {
            channel = connectFuture.sync().channel();
        } catch (Exception e) {
            // TODO 需要进一步验证这种机制，是否可以确保关闭连接。
            connectFuture.cancel(true);
            channel = connectFuture.channel();
            if (channel != null) {
                channel.close();
            }
            System.out.println("connect server fail " + host + ":" + port);
            e.printStackTrace();
        }
        return channel;
    }

    public static void main(String[] args) {
        try {
            new NettyClient().connect(NetConfig.SERVER_IP, NetConfig.SERVER_PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
