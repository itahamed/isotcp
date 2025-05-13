package com.it.iso.service;

import com.it.iso.config.Iso8583Properties;
import com.it.iso.model.IsoJsonMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

/**
 * Client for domain microservices
 */
@Slf4j
@Service
public class MicroserviceClient {
    
    @Autowired
    private Iso8583Properties properties;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * Send ISO JSON message to domain microservice
     */
    public IsoJsonMessage sendToMicroservice(IsoJsonMessage request, String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<IsoJsonMessage> entity = new HttpEntity<>(request, headers);
        
        try {
            String url = "http://" + properties.getMicroserviceBaseUrl() + "/api/" + endpoint;
            log.info("Sending request to microservice: {}", url);
            
            ResponseEntity<IsoJsonMessage> response = restTemplate.postForEntity(
                url, entity, IsoJsonMessage.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                log.error("Error from microservice: {}", response.getStatusCode());
                IsoJsonMessage errorResponse = new IsoJsonMessage();
                errorResponse.setMessageType(request.getMessageType());
                errorResponse.setResponseCode("96"); // System error
                return errorResponse;
            }
        } catch (Exception e) {
            log.error("Exception calling microservice", e);
            IsoJsonMessage errorResponse = new IsoJsonMessage();
            errorResponse.setMessageType(request.getMessageType());
            errorResponse.setResponseCode("96"); // System error
            return errorResponse;
        }
    }
}
