package com.it.iso.client.model;

import java.util.Map;
import java.util.HashMap;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * JSON representation of an ISO8583 message
 */
@Getter
@Setter
public class IsoJsonMessage {
    private String messageType;
    private Map<Integer, String> fields = new HashMap<>();
    
    // Additional metadata fields
    private String sourcePort;
    private String responseCode;
    private String processingCode;
}