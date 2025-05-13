package com.it.iso.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;

/**
 * Configuration for ISO8583 message factory
 */
@Configuration
public class MessageFactoryConfig {
    
    @Bean
    public MessageFactory<IsoMessage> iso8583MessageFactory() throws Exception {
        MessageFactory<IsoMessage> mf = new MessageFactory<>();
        mf.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mf.setUseBinaryMessages(false);
        mf.setTraceNumberGenerator(new SimpleTraceGenerator((int) (System.currentTimeMillis() % 1000000)));
        
        // Load message type configurations from classpath
        ConfigParser.configureFromClasspathConfig(mf, "iso8583/iso8583-config.xml");
        
        return mf;
    }
}