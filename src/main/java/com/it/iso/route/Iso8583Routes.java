package com.it.iso.route;

import com.it.iso.config.Iso8583Properties;
import com.it.iso.converter.IsoMessageConverter;
import com.it.iso.model.IsoJsonMessage;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;


/**
 * Camel routes for ISO8583 message processing
 */
@Slf4j
@Component
public class Iso8583Routes extends RouteBuilder {
    
    @Autowired
    private Iso8583Properties properties;
    
    @Autowired
    private IsoMessageConverter converter;
    
    @Autowired
    private MessageFactory<IsoMessage> messageFactory;
    
    @Override
    public void configure() throws Exception {
        // Error handler
        errorHandler(deadLetterChannel("direct:error")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .useOriginalMessage());

        // Error processing route
        from("direct:error")
            .log("Error processing ISO message: ${exception.message}")
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                IsoMessage requestMessage = exchange.getIn().getBody(IsoMessage.class);
                if (requestMessage != null) {
                    // Create an error response
                    IsoJsonMessage errorJson = new IsoJsonMessage();
                    errorJson.setResponseCode("96"); // System error

                    IsoMessage errorResponse = converter.createResponseMessage(requestMessage, errorJson);
                    exchange.getIn().setBody(errorResponse);
                }
            })
            // Send back to the original endpoint handler
            .to("direct:sendResponse");

        // Route for first TCP port

       /* from("netty:tcp://" + properties.getTcpServer().getHost() + ":" + properties.getTcpServer().getPort1() + "?sync=true")
            .id("port1-receiver")
            .log("Received ISO message on port " + properties.getTcpServer().getPort1())
            .setHeader("SourcePort", constant(String.valueOf(properties.getTcpServer().getPort1())))
            .to("direct:processIsoMessage");

        // Route for second TCP port
        from("netty:tcp://" + properties.getTcpServer().getHost() + ":" + properties.getTcpServer().getPort2() + "?sync=true")
            .id("port2-receiver")
            .log("Received ISO message on port " + properties.getTcpServer().getPort2())
            .setHeader("SourcePort", constant(String.valueOf(properties.getTcpServer().getPort2())))
            .to("direct:processIsoMessage");*/

        createTcpListenerRoute(properties.getTcpServer().getPort1(), "port1-receiver");

        createTcpListenerRoute(properties.getTcpServer().getPort2(), "port2-receiver");

        // Common ISO message processing
        from("direct:processIsoMessage")
            .id("iso-processor")
            .log("Processing ISO Message: ${body}")
            .process(exchange -> {
                // Convert ISO message to JSON
                IsoMessage isoMessage = exchange.getIn().getBody(IsoMessage.class);
                String sourcePort = exchange.getIn().getHeader("SourcePort", String.class);

                // Store original message for response creation
                exchange.setProperty("originalIsoMessage", isoMessage);

                // Convert to JSON format
                IsoJsonMessage jsonMessage = converter.isoToJson(isoMessage, sourcePort);
                exchange.getIn().setBody(jsonMessage);
            })
            // Identify message type and route accordingly
            .choice()
                .when(simple("${body.messageType} == '0200'"))
                    .log("Processing financial request")
                    .setHeader("TargetServicePath", constant("/api/financial"))
                    .to("direct:invokeGenericMicroservice")
                .when(simple("${body.messageType} == '0100'"))
                    .log("Processing authorization request")
                    .setHeader("TargetServicePath", constant("/api/authorization"))
                    .to("direct:invokeGenericMicroservice")
                .when(simple("${body.messageType} == '0400'"))
                    .log("Processing reversal request")
                    .setHeader("TargetServicePath", constant("/api/reversal"))
                    .to("direct:invokeGenericMicroservice")
                .when(simple("${body.messageType} == '0800'"))
                    .log("Processing network management request")
                    .setHeader("TargetServicePath", constant("/api/network"))
                    .to("direct:invokeGenericMicroservice")
                .otherwise()
                    .log("Unknown message type: ${body.messageType}")
                    .process(exchange -> {
                        IsoJsonMessage jsonMessage = exchange.getIn().getBody(IsoJsonMessage.class);
                        jsonMessage.setResponseCode("96"); // Invalid message type
                        exchange.getIn().setBody(jsonMessage);
                    })
                    .to("direct:prepareResponse")
            .end();

       /* // Financial service route
        from("direct:invokeFinancialService")
            .marshal().json(JsonLibrary.Jackson)
            .log("Sending to financial service: ${body}")
            .to("http://" + properties.getMicroserviceBaseUrl() + "/api/financial")
            .unmarshal().json(JsonLibrary.Jackson, IsoJsonMessage.class)
            .to("direct:prepareResponse");

        // Authorization service route
        from("direct:invokeAuthorizationService")
            .marshal().json(JsonLibrary.Jackson)
            .log("Sending to authorization service: ${body}")
            .to("http://" + properties.getMicroserviceBaseUrl() + "/api/authorization")
            .unmarshal().json(JsonLibrary.Jackson, IsoJsonMessage.class)
            .to("direct:prepareResponse");

        // Reversal service route
        from("direct:invokeReversalService")
            .marshal().json(JsonLibrary.Jackson)
            .log("Sending to reversal service: ${body}")
            .to("http://" + properties.getMicroserviceBaseUrl() + "/api/reversal")
            .unmarshal().json(JsonLibrary.Jackson, IsoJsonMessage.class)
            .to("direct:prepareResponse");

        // Network management service route
        from("direct:invokeNetworkService")
            .marshal().json(JsonLibrary.Jackson)
            .log("Sending to network service: ${body}")
            .to("http://" + properties.getMicroserviceBaseUrl() + "/api/network")
            .unmarshal().json(JsonLibrary.Jackson, IsoJsonMessage.class)
            .to("direct:prepareResponse");*/

        from("direct:invokeGenericMicroservice")
                .id("generic-microservice-invoker")
                .marshal().json(JsonLibrary.Jackson)
                .log("Sending to microservice at ${header.TargetServicePath}: ${body}")
                // Dynamically build the recipient URI
                .recipientList(simple("http://" + properties.getMicroserviceBaseUrl() + "${header.TargetServicePath}"))
                .unmarshal().json(JsonLibrary.Jackson, IsoJsonMessage.class)
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
                log.info("Response ISO message: {}", exchange.getIn().getBody(IsoMessage.class).debugString());
            });
    }

    private void createTcpListenerRoute(int port, String routeId) {
        from("netty:tcp://" + properties.getTcpServer().getHost() + ":" + port + "?sync=true")
                .id(routeId)
                .log("Received ISO message on port " + port)
                .setHeader("SourcePort", constant(String.valueOf(port)))
                .to("direct:processIsoMessage");
    }
}