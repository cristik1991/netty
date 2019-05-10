package com.cristik.netty.example.encode.marshalling;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author cristik
 */
public class ServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("ServerHandler2 receive msg:" + msg.toString());
        ctx.channel().writeAndFlush("this is ServerHandler2 reply msg happend at !" + System.currentTimeMillis());
    }

}
