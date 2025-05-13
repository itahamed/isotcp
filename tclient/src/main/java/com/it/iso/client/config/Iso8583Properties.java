package com.it.iso.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration properties for the application
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "iso8583")
public class Iso8583Properties {
    private String microserviceBaseUrl;
    private TcpServer tcpServer;
    
    @Data
    public static class TcpServer {
        private String host;
        private int port1;
        private int port2;
    }
}