package com.it.iso.model;

import java.util.Map;
import java.util.HashMap;
import lombok.Data;

/**
 * JSON representation of an ISO8583 message
 */
@Data
public class IsoJsonMessage {
    private String messageType;
    private Map<Integer, String> fields = new HashMap<>();
    
    // Additional metadata fields
    private String sourcePort;
    private String responseCode;
}