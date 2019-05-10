package com.cristik.netty.proxy.mysql.conf;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.cristik.netty.proxy.mysql.utils.ParameterMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author crisik
 */
public class SystemConfig {
    
	private static Logger logger = LoggerFactory.getLogger(SystemConfig.class);
	private static final String DEFAULT_CONFIG_FILE = "/system.properties";
	
    private String mysqlHost = "139.217.228.225";
    private int mysqlPort = 3306;
    private String bindIp = "localhost";
    private int serverPort = 9999;
    
    private int bossThdCnt = 1;
    private int workThdCnt = Runtime.getRuntime().availableProcessors();
    
    public void load() {
        // load system properties
    	Properties props = new Properties();
        try {
            if (SystemConfig.class.getResourceAsStream(DEFAULT_CONFIG_FILE) == null) {
                return;
            }
            props.load(SystemConfig.class.getResourceAsStream(DEFAULT_CONFIG_FILE));
            Map<String, String> map = new HashMap<>();
            for(Object key : props.keySet()) {
                map.put(key.toString(), props.getProperty(key.toString()));
            }
            ParameterMapping.mapping(this, map);
        } catch (Exception e) {
        	logger.error(e.getMessage(), e);
        }
    }
    
    public String getMysqlHost() {
        return mysqlHost;
    }
    public void setMysqlHost(String mysqlHost) {
        this.mysqlHost = mysqlHost;
    }
    public int getMysqlPort() {
        return mysqlPort;
    }
    public void setMysqlPort(int mysqlPort) {
        this.mysqlPort = mysqlPort;
    }

	public String getBindIp() {
		return bindIp;
	}

	public void setBindIp(String bindIp) {
		this.bindIp = bindIp;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	public int getBossThdCnt() {
		return bossThdCnt;
	}

	public void setBossThdCnt(int bossThdCnt) {
		this.bossThdCnt = bossThdCnt;
	}

	public int getWorkThdCnt() {
		return workThdCnt;
	}

	public void setWorkThdCnt(int workThdCnt) {
		this.workThdCnt = workThdCnt;
	}
}
