package com.it.iso.client.infra.rest.mapper; // Or your preferred package for mappers

import com.it.iso.client.model.IsoJsonMessage;
import com.it.iso.client.infra.rest.stub.FMSRequest; // Your FMSRequest class
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Map;

@Mapper(componentModel = "spring") // Makes it a Spring-managed bean
public interface IsoJsonToFmsRequestMapper {

    // Instance for manual use if not using Spring injection
    // IsoJsonToFmsRequestMapper INSTANCE = Mappers.getMapper(IsoJsonToFmsRequestMapper.class);

    @Mapping(target = "processingCode", source = "processingCode") // Use the dedicated field
    @Mapping(target = "cardNo", source = "fields", qualifiedByName = "mapField2")
    @Mapping(target = "amount", source = "fields", qualifiedByName = "mapField4")
    @Mapping(target = "transactionType", source = "messageType") // MTI as transaction type
    @Mapping(target = "customerId", source = "fields", qualifiedByName = "mapField2") // Example: using PAN as customerId
    @Mapping(target = "customerName", source = "fields", qualifiedByName = "mapField100") // Example: placeholder for customer name
    FMSRequest toFmsRequest(IsoJsonMessage isoJsonMessage);

    @Named("mapField2")
    default String mapField2(Map<Integer, String> fields) {
        return fields != null ? fields.get(2) : null; // Field 2: Primary Account Number (PAN)
    }

    @Named("mapField4")
    default String mapField4(Map<Integer, String> fields) {
        return fields != null ? fields.get(4) : null; // Field 4: Amount, Transaction
    }

    @Named("mapField100")
    default String mapField100(Map<Integer, String> fields) {
        // Example: Assuming customer name might be in a custom field like 100
        // Adjust this field number based on your actual ISO8583 specification
        return fields != null ? fields.get(100) : null;
    }
}