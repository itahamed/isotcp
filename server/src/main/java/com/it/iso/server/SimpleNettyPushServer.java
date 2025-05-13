package com.it.iso.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SimpleNettyPushServer {

    // Shared group of channels (thread-safe)
    private static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ChannelPipeline pipeline = ch.pipeline();
                     pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                         @Override
                         public void channelActive(ChannelHandlerContext ctx) {
                             System.out.println("Client connected: " + ctx.channel().remoteAddress());
                             allChannels.add(ctx.channel());
                         }

                         @Override
                         protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                             System.out.println("Received from client: " + msg);
                         }

                         @Override
                         public void channelInactive(ChannelHandlerContext ctx) {
                             System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
                         }
                     });
                 }
             });

            ChannelFuture future = b.bind(5150).sync();
            System.out.println("Server started on port 5150");

            // Start console input thread to send messages to clients
            Thread consoleInputThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.print("Enter message to send: ");
                    String line = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(line)) {
                        System.out.println("Shutting down server...");
                        future.channel().close(); // Close the server channel
                        break;
                    }
                    allChannels.writeAndFlush(line + "\n");
                }
                scanner.close();
            });
            consoleInputThread.start();

            future.channel().closeFuture().sync(); // Wait for server to close
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
