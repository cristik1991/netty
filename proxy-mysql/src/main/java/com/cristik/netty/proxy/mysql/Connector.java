package com.cristik.netty.proxy.mysql;

import java.net.InetSocketAddress;

import com.cristik.netty.proxy.mysql.handler.BackendConnectionHandler;
import com.cristik.netty.proxy.mysql.handler.BackendConnectionLogHandler;
import com.cristik.netty.proxy.mysql.protocol.ErrorPacket;
import com.cristik.netty.proxy.mysql.utils.IpUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cristik
 */
public class Connector {

    private static final Logger logger = LoggerFactory.getLogger(Connector.class);
    private Bootstrap bootstrap;
    
    public Connector(EventLoopGroup group) {
    	final BackendConnectionLogHandler logHandler = new BackendConnectionLogHandler();
    	final BackendConnectionHandler mainHandler = new BackendConnectionHandler();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
        	.channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
            	
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(logHandler, mainHandler);
                }
                
            });
    }
    
    public void connect(String host, int port, final Channel frontend) {
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        ChannelFutureListener listener = future1 -> {
            if (future1.isSuccess()) {
                Channel backend = future1.channel();
                Session session = new Session();
                logger.info("on channel connect future operationComplete, bind channel, frontend : [{}], backend : [{}]",
                        IpUtil.getRemoteAddress(frontend), IpUtil.getAddress(backend));
                session.bind(frontend, backend);
                backend.attr(Session.SESSION_KEY).set(session);
                frontend.attr(Session.SESSION_KEY).set(session);
            } else {
                logger.error("channel connect fail", future1.cause());
                ErrorPacket.build(2003, future1.cause().getMessage()).write(frontend, true);
            }
        };
        future.addListener(listener);
    }
    
}
