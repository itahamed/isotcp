package com.it.iso.client.converter;
import java.util.Map;

import com.it.iso.client.model.IsoJsonMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import com.solab.iso8583.MessageFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Converter between ISO8583 messages and JSON format
 */
@Slf4j
@Component
public class IsoMessageConverter {

    @Autowired
    private MessageFactory<IsoMessage> messageFactory;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Convert ISO8583 message to JSON
     */
    public IsoJsonMessage isoToJson(IsoMessage isoMessage, String sourcePort) {
        IsoJsonMessage jsonMessage = new IsoJsonMessage();
        jsonMessage.setMessageType(String.valueOf(isoMessage.getType()));
        jsonMessage.setSourcePort(sourcePort);

        if (isoMessage.hasField(3)) {
            String processingCode = isoMessage.getObjectValue(3).toString();
            jsonMessage.setProcessingCode(processingCode);
            log.debug("Extracted Processing Code (Field 3): {}", processingCode);
        } else {
            log.error("ISO message does not have Field 3 (Processing Code)");
        }

        // Extract all fields from the ISO message
        for (int i = 2; i <= 128; i++) {
            if (isoMessage.hasField(i)) {
                jsonMessage.getFields().put(i, isoMessage.getObjectValue(i).toString());
            }
        }

        return jsonMessage;
    }

    /**
     * Convert JSON to ISO8583 message
     */
    public IsoMessage jsonToIso(IsoJsonMessage jsonMessage) throws Exception {
        // Create a new ISO message based on the message type
        IsoMessage isoMessage = messageFactory.newMessage(Integer.parseInt(jsonMessage.getMessageType()));

        // Set values from the fields map
        for (Map.Entry<Integer, String> entry : jsonMessage.getFields().entrySet()) {
            int fieldNum = entry.getKey();
            String value = entry.getValue();

            try {
                // Get field metadata from template to determine format
                IsoType fieldType = messageFactory.getMessageTemplate(Integer.parseInt(jsonMessage.getMessageType()))
                        .getField(fieldNum).getType();

                // Create an IsoValue with the proper type and value
                IsoValue<?> isoValue = new IsoValue<>(fieldType, value);

                // Set the field in the message
                isoMessage.setField(fieldNum, isoValue);
            } catch (Exception e) {
                log.error("Error setting field {}: {}", fieldNum, e.getMessage());
                throw e;
            }
        }

        return isoMessage;
    }

    /**
     * Create response message based on request
     */
    public IsoMessage createResponseMessage(IsoMessage requestMessage, IsoJsonMessage responseJson) throws Exception {
        // Create a response with appropriate message type (usually original type + 10)
        String requestType = String.valueOf(requestMessage.getType());
        int responseType = Integer.parseInt(requestType) + 10;

        IsoMessage responseMessage = messageFactory.newMessage(responseType);

        // Copy key fields from request
        if (requestMessage.hasField(3)) {
            responseMessage.setField(3, requestMessage.getField(3)); // Processing code
        }

        if (requestMessage.hasField(4)) {
            responseMessage.setField(4, requestMessage.getField(4)); // Amount
        }

        if (requestMessage.hasField(11)) {
            responseMessage.setField(11, requestMessage.getField(11)); // STAN
        }

        // Set the response code from microservice response
        if (responseJson.getResponseCode() != null) {
            IsoValue<String> responseCodeValue = new IsoValue<>(IsoType.ALPHA, responseJson.getResponseCode(), 2);
            responseMessage.setField(39, responseCodeValue);
        } else {
            // Default approved
            IsoValue<String> responseCodeValue = new IsoValue<>(IsoType.ALPHA, "00", 2);
            responseMessage.setField(39, responseCodeValue);
        }

        // Set additional fields from the JSON response
        for (Map.Entry<Integer, String> entry : responseJson.getFields().entrySet()) {
            int fieldNum = entry.getKey();
            String value = entry.getValue();

            // Skip fields already set and validation fields
            if (fieldNum != 3 && fieldNum != 4 && fieldNum != 11 && fieldNum != 39) {
                try {
                    IsoType fieldType = messageFactory.getMessageTemplate(responseType)
                            .getField(fieldNum).getType();

                    IsoValue<?> isoValue = new IsoValue<>(fieldType, value);
                    responseMessage.setField(fieldNum, isoValue);
                } catch (Exception e) {
                    log.warn("Skipping field {} due to error: {}", fieldNum, e.getMessage());
                }
            }
        }

        return responseMessage;
    }
}