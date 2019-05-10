package com.cristik.netty.proxy.mysql;

import com.cristik.netty.proxy.mysql.conf.SystemConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;


/**
 * @author cristik
 */
public class ProxyServer {
    
    private static final  Logger logger = LoggerFactory.getLogger(ProxyServer.class );
    
    private static ThreadFactory bossGroupThreadFactory = new ThreadFactoryBuilder().setNameFormat("bossGroup-%d").build();
    private static ThreadFactory workGroupThreadFactory = new ThreadFactoryBuilder().setNameFormat("workGroup-%d").build();
    private static ProxyServer instance;
    
    private SystemConfig systemConf;
    private Acceptor acceptor;
    private Connector connector;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    
    private String host;
    private int port;
    
    public static ProxyServer getInstance() {
        return instance;
    }
    
    public ProxyServer() {
        systemConf = new SystemConfig();
        systemConf.load();
        this.host = systemConf.getBindIp();
        this.port = systemConf.getServerPort();
        // for accepting frontend connection
        bossGroup = new NioEventLoopGroup(systemConf.getBossThdCnt(), bossGroupThreadFactory);
        // for IO R/W
        workGroup = new NioEventLoopGroup(systemConf.getWorkThdCnt(), workGroupThreadFactory);
        connector = new Connector(workGroup);
        acceptor = new Acceptor(bossGroup, workGroup, host, port);
        instance = this;
    }
    
    public void start() {
        try {
            acceptor.start();
        } catch (InterruptedException e) {
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
    
    public static void main(String[] args) {
        ProxyServer server = new ProxyServer();
        server.start();
    }
    
    public Connector getConnector() {
        return this.connector;
    }
    
    public SystemConfig getSystemConfig() {
        return this.systemConf;
    }

}
