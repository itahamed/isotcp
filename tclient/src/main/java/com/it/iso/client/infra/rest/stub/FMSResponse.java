package com.it.iso.client.infra.rest.stub;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FMSResponse {

    private String transactionId; // Example field
    private String status;        // Example: "SUCCESS", "FAILURE"
    private String responseCode;  // e.g., "00" for success, maps to ISO Field 39
    private String authId;        // Maps to ISO Field 38
    private String message;
}
