package com.cristik.netty.example.encode.marshalling;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author cristik
 */
public class Server {

    public static void main(String[] args) throws Exception{

        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new StringDecoder());
                        pipeline.addLast(new ServerHandler());
                        pipeline.addLast(new StringEncoder());
                    }
                });
        //连接缓冲池的大小
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 2048);
        //维持链接的活跃，清除死链接
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        //关闭延迟发送
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);


        try {
            ChannelFuture channelFuture = serverBootstrap.bind(8765).sync();
            //等待服务关闭，关闭后应该释放资源
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            System.out.println("server start got exception!");
            e.printStackTrace();
        }finally {
            //8.优雅的关闭资源
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }

    }

}
