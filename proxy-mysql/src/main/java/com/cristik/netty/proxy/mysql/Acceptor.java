package com.cristik.netty.proxy.mysql;

import java.net.InetSocketAddress;

import com.cristik.netty.proxy.mysql.handler.FrontendConnectionHandler;
import com.cristik.netty.proxy.mysql.handler.FrontendConnectionLogHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cristik
 */
public class Acceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(Acceptor.class);

    private String host;
    private int port;
    private ServerBootstrap serverBootstrap;
    
    private static Acceptor instance;
    
    public static Acceptor getInstance() {
        return instance;
    }
    
    public Acceptor(EventLoopGroup bossGroup, EventLoopGroup workGroup, String host, int port) {
        this.host = host;
        this.port = port;
        serverBootstrap = new ServerBootstrap();
        final FrontendConnectionLogHandler logHandler = new FrontendConnectionLogHandler();
        final FrontendConnectionHandler mainHandler = new FrontendConnectionHandler();
        serverBootstrap.group(bossGroup, workGroup)
        	.channel(NioServerSocketChannel.class)
        	.localAddress(new InetSocketAddress(host, port))
        	.childHandler(new ChannelInitializer<SocketChannel>() {
	            @Override
	            protected void initChannel(SocketChannel ch) throws Exception {
	                ch.pipeline().addLast(logHandler, mainHandler);
	            }
            });
        instance = this;
    }

    public void start() throws InterruptedException {
        ChannelFuture future = serverBootstrap.bind().sync();
        logger.info("server bind on {}:{}", this.host, this.port);
        future.channel().closeFuture().sync();
    }
    
}
