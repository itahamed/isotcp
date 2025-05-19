package com.it.iso.client.config;

import java.util.List;

import com.it.iso.client.codec.IsoMessageDecoder;
import com.it.iso.client.codec.IsoMessageEncoder;
import org.apache.camel.CamelContext;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;

/**
 * Configuration for Netty components
 */
@Configuration
public class NettyConfig {
    
    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private MessageFactory<IsoMessage> messageFactory;
    
    @Bean(name = "netty")
    public NettyComponent nettyComponent(NettyConfiguration nettyPort1Config, NettyConfiguration nettyPort2Config) {
        NettyComponent netty = new NettyComponent();
        netty.setConfiguration(nettyPort1Config);
        netty.setConfiguration(nettyPort2Config);
        netty.setCamelContext(camelContext);
        return netty;
    }
    
    @Bean(name = "nettyPort1Config")
    public NettyConfiguration nettyPort1Config() {
        NettyConfiguration config = new NettyConfiguration();

        config.setProtocol("tcp");
        config.setClientMode(true);

        config.setSync(true);
        config.setReuseAddress(true);
        config.setDisconnect(false);

        // Add decoder and encoder
        config.setDecoders(List.of(new IsoMessageDecoder(messageFactory)));
        config.setEncoders(List.of(new IsoMessageEncoder()));

        return config;
    }
    
    @Bean(name = "nettyPort2Config")
    public NettyConfiguration nettyPort2Config() {
        NettyConfiguration config = new NettyConfiguration();
        config.setProtocol("tcp");

        config.setSync(true);
        config.setReuseAddress(true);
        config.setDisconnect(false);
        
        // Add decoder and encoder
        config.setDecoders(List.of(new IsoMessageDecoder(messageFactory)));
        config.setEncoders(List.of(new IsoMessageEncoder()));
        
        return config;
    }
}