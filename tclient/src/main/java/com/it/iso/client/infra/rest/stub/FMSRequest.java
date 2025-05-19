package com.it.iso.client.infra.rest.stub;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FMSRequest {

    private String transactionId;

    private String customerId;

    private String cardNo;

    private String transactionType;

    private String processingCode;

    private String customerName;

    private String amount;

}
