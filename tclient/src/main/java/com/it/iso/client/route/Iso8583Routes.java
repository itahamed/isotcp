package com.it.iso.client.route;

import com.it.iso.client.config.Iso8583Properties;
import com.it.iso.client.infra.rest.mapper.FmsResponseToIsoJsonMapper;
import com.it.iso.client.infra.rest.mapper.IsoJsonToFmsRequestMapper;
import com.it.iso.client.converter.IsoMessageConverter;
import com.it.iso.client.infra.rest.stub.FMSRequest;
import com.it.iso.client.infra.rest.stub.FMSResponse;
import com.it.iso.client.model.IsoJsonMessage;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;

import java.io.ByteArrayOutputStream;


/**
 * Camel routes for ISO8583 message processing
 */
@Slf4j
@Component
public class Iso8583Routes extends RouteBuilder {

    // Response Codes
    private static final String RC_96_SYSTEM_MALFUNCTION = "96";

    @Autowired
    private Iso8583Properties properties;
    
    @Autowired
    private IsoMessageConverter converter;
    
    @Autowired
    private MessageFactory<IsoMessage> messageFactory;

    @Autowired
    private IsoJsonToFmsRequestMapper fmsRequestMapper; // Autowire the mapper

    @Autowired
    private FmsResponseToIsoJsonMapper fmsResponseMapper;

    @Override
    public void configure() throws Exception {

        // Error handler
        /*errorHandler(deadLetterChannel("direct:error")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .useOriginalMessage());*/

        // Error processing route
        from("direct:error")
                .log("Error processing ISO message: ${exception.message}")
                .process(exchange -> {
                    Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    IsoMessage requestMessage = exchange.getIn().getBody(IsoMessage.class);

                    if (requestMessage != null) {
                        // Create an error response
                        IsoJsonMessage errorJson = new IsoJsonMessage();
                        errorJson.setResponseCode(RC_96_SYSTEM_MALFUNCTION); // System error

                        IsoMessage errorResponse = converter.createResponseMessage(requestMessage, errorJson);
                        exchange.getIn().setBody(errorResponse);
                    }else{
                        log.warn("Could not retrieve original IsoMessage to create an error response for exception: {}", exception.getMessage());
                    }
                })
                // Send back to the original endpoint handler
                .to("direct:sendResponse");

        // Route for first TCP port
        createTcpListenerRoute(properties.getTcpServer().getPort1(), "port1-receiver");
      //  createTcpListenerRoute(properties.getTcpServer().getPort2(), "port2-receiver");

        // Common ISO message processing
        from("direct:processIsoMessage")
            .id("iso-processor")
            .log("Processing ISO Message: ${body}")
            .process(exchange -> {

                // Convert ISO message to JSON
                String incoming = exchange.getIn().getBody(String.class);
                IsoMessage isoMessage = messageFactory.parseMessage(incoming.getBytes(), 12);

                String sourcePort = exchange.getIn().getHeader("SourcePort", String.class);

                // Store original message for response creation
                exchange.setProperty("originalIsoMessage", isoMessage);

                // Convert to JSON format
                IsoJsonMessage jsonMessage = converter.isoToJson(isoMessage, sourcePort);
                exchange.getIn().setBody(jsonMessage);
            })
            // Identify message type and route accordingly
            .choice()
                .when(simple("${body.messageType} == '528'"))
                    .log("Processing financial request")
                    .setHeader("TargetServicePath", constant("/api/financial"))
                    .to("direct:invokeGenericMicroservice")
                .otherwise()
                    .log("Unknown message type: ${body.messageType}")
                    .process(exchange -> {
                        IsoJsonMessage jsonMessage = exchange.getIn().getBody(IsoJsonMessage.class);
                        jsonMessage.setResponseCode(RC_96_SYSTEM_MALFUNCTION); // Invalid message type
                        exchange.getIn().setBody(jsonMessage);
                    })
                    .to("direct:prepareResponse")
            .end();

        from("direct:invokeGenericMicroservice")
                .id("generic-microservice-invoker")
                .log("Sending to microservice at ${header.TargetServicePath}: ${body}")

                .process(exchange -> {

                    IsoJsonMessage requestIsoMessage = exchange.getIn().getBody(IsoJsonMessage.class);

                    // 1. Retrieve processing code from IsoJsonMessage
                    String processingCode = requestIsoMessage.getProcessingCode();
                    log.info("Retrieved Processing Code for FMS mapping: {}", processingCode);
                    // The processingCode is already part of isoJsonMessage and will be mapped by MapStruct

                    // 2. Use mapstruct to map data from IsoJsonMessage to FMSRequest
                    FMSRequest fmsRequest = fmsRequestMapper.toFmsRequest(requestIsoMessage);
                    log.info("Mapped IsoJsonMessage to FMSRequest: {}", fmsRequest); // Ensure FMSRequest has a meaningful toString() or use a JSON logger

                    exchange.getIn().setBody(fmsRequest);

                })
                // 3. Construct API request header & 4. Call /api/financial post endpoint
                .marshal().json(JsonLibrary.Jackson) // Convert FMSRequest to JSON string
                .log("Marshalled FMSRequest to JSON: ${body}")
                .removeHeaders("CamelHttp*") // Clean up potential old HTTP headers
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD("netty-http:http://" + properties.getMicroserviceBaseUrl() + "${header.TargetServicePath}" + "?bridgeEndpoint=true")
                .log("Response from microservice: ${body}")

                // 5. Process the json response and convert to FMSResponse object
                .unmarshal().json(JsonLibrary.Jackson, FMSResponse.class)
                .log("Unmarshalled JSON response to FMSResponse: ${body}")

                // 6. Construct new IsoJsonMessage and use mapstruct to map data from FMSResponse and original requestIsoMessage
                .process(exchange -> {
                    FMSResponse fmsResponse = exchange.getIn().getBody(FMSResponse.class);
                    IsoJsonMessage originalRequestIsoJsonMessage = exchange.getProperty("originalRequestIsoJsonMessage", IsoJsonMessage.class);

                    if (originalRequestIsoJsonMessage == null) {
                        log.error("Original IsoJsonMessage not found in exchange properties for response mapping.");
                        // Handle error appropriately, perhaps by creating a default error IsoJsonMessage
                        throw new IllegalStateException("Original IsoJsonMessage (originalRequestIsoJsonMessage) not found.");
                    }

                    IsoJsonMessage finalIsoJsonResponse = fmsResponseMapper.toIsoJsonMessage(fmsResponse, originalRequestIsoJsonMessage);
                    log.info("Mapped FMSResponse to final IsoJsonMessage: {}", finalIsoJsonResponse);
                    exchange.getIn().setBody(finalIsoJsonResponse);
                })

                .to("direct:prepareResponse");

        // Prepare ISO response
        from("direct:prepareResponse")
            .log("Preparing ISO response")
            .process(exchange -> {
                IsoJsonMessage responseJson = exchange.getIn().getBody(IsoJsonMessage.class);
                IsoMessage originalMessage = exchange.getProperty("originalIsoMessage", IsoMessage.class);

                // Create ISO response message
                IsoMessage responseMessage = converter.createResponseMessage(originalMessage, responseJson);
                exchange.getIn().setBody(responseMessage);
            })
            .to("direct:sendResponse");

        // Send response back through same port
        from("direct:sendResponse")
                .log("Sending ISO response: ${body}")
                .process(exchange -> {
                    log.info("sendResponse: ISO message: {}", exchange.getIn().getBody(IsoMessage.class).debugString());

                    IsoMessage isoMessage = exchange.getIn().getBody(IsoMessage.class);

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    isoMessage.write(byteArrayOutputStream, 0);
                    byte[] responseBytes = byteArrayOutputStream.toByteArray();

                    // Retrieve the original Netty channel
                    io.netty.channel.Channel originalChannel = exchange.getProperty("OriginalNettyChannel", io.netty.channel.Channel.class);

                    if (originalChannel != null && originalChannel.isActive()) {
                        log.info("Sending response bytes to original Netty channel: {}", originalChannel.remoteAddress());
                        originalChannel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(responseBytes));
                    } else {
                        log.warn("OriginalNettyChannel not found or inactive. Cannot send response for exchangeId: {}", exchange.getExchangeId());
                    }

                    log.info("sendResponse: response: {}", responseBytes);
                    exchange.getMessage().setBody(responseBytes);
                });
    }

    private void createTcpListenerRoute(int port, String routeId) {
        from("netty:tcp://" + properties.getTcpServer().getHost() + ":" + port
                       + "?sync=false&clientMode=true"
        )
                .id(routeId)
                .log("Received ISO message on port " + port)
                .setHeader("SourcePort", constant(String.valueOf(port)))
                .setProperty("OriginalNettyChannel", simple("${header.CamelNettyChannel}"))
                .to("direct:processIsoMessage");
    }
}