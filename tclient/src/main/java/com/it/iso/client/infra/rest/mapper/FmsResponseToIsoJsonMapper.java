package com.it.iso.client.infra.rest.mapper;

import com.it.iso.client.infra.rest.stub.FMSResponse;
import com.it.iso.client.model.IsoJsonMessage;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.AfterMapping;
import org.mapstruct.MappingTarget;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface FmsResponseToIsoJsonMapper {

    // Primary mapping from FMSResponse to IsoJsonMessage
    // We only map direct fields from fmsResponse here or ignore fields handled in @AfterMapping
    @Mapping(target = "responseCode", source = "fmsResponse.responseCode")
    @Mapping(target = "messageType", ignore = true) // Handled in @AfterMapping
    @Mapping(target = "fields", ignore = true)      // Handled in @AfterMapping
    @Mapping(target = "sourcePort", ignore = true)   // Handled in @AfterMapping
    @Mapping(target = "processingCode", ignore = true) // Handled in @AfterMapping
    IsoJsonMessage toIsoJsonMessage(FMSResponse fmsResponse, @Context IsoJsonMessage originalIsoJsonMessage);

    @AfterMapping
    default void populateFieldsAndContextualData(@MappingTarget IsoJsonMessage target, FMSResponse fmsResponse, @Context IsoJsonMessage originalIsoJsonMessage) {
        // Ensure target and originalIsoJsonMessage are not null to avoid NullPointerExceptions
        if (target == null) {
            return; // Or throw an IllegalArgumentException
        }

        Map<Integer, String> fields = new HashMap<>();
        if (originalIsoJsonMessage != null && originalIsoJsonMessage.getFields() != null) {
            fields.putAll(originalIsoJsonMessage.getFields());
        }

        // Override/add fields from FMSResponse
        if (fmsResponse != null) { // Check if fmsResponse is not null
            if (fmsResponse.getResponseCode() != null) {
                fields.put(39, fmsResponse.getResponseCode()); // Field 39: Response Code
            }
            if (fmsResponse.getAuthId() != null) {
                fields.put(38, fmsResponse.getAuthId()); // Field 38: Authorization ID Response
            }
            // Example: if FMSResponse has a transaction ID that needs to go into a specific ISO field
            // if (fmsResponse.getTransactionId() != null) {
            //     fields.put(37, fmsResponse.getTransactionId()); // Field 37: Retrieval Reference Number
            // }
        }
        target.setFields(fields);

        // Set fields from the originalIsoJsonMessage context
        if (originalIsoJsonMessage != null) {
            target.setSourcePort(originalIsoJsonMessage.getSourcePort());
            target.setProcessingCode(originalIsoJsonMessage.getProcessingCode());

            // Set the MTI for the response. Typically request MTI + 10.
            if (originalIsoJsonMessage.getMessageType() != null) {
                try {
                    int requestMti = Integer.parseInt(originalIsoJsonMessage.getMessageType());
                    target.setMessageType(String.format("%04d", requestMti + 10));
                } catch (NumberFormatException e) {
                    // Log error or handle appropriately
                    target.setMessageType(originalIsoJsonMessage.getMessageType()); // Fallback
                }
            }
        } else {
            // Handle cases where originalIsoJsonMessage might be null, if applicable
            // For example, set default values or log a warning
        }
    }
}