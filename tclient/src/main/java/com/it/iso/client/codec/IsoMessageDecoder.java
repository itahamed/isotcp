package com.it.iso.client.codec;

import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;

/**
 * Netty decoder for ISO8583 messages
 */
@Slf4j
public class IsoMessageDecoder extends ByteToMessageDecoder {

    private final MessageFactory<IsoMessage> messageFactory;

    public IsoMessageDecoder(MessageFactory<IsoMessage> messageFactory) {
        this.messageFactory = messageFactory;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // We need at least 2 bytes for length
        if (in.readableBytes() < 2) {
            return;
        }

        in.markReaderIndex();

        // Read message length (first 2 bytes)
        short length = in.readShort();

        // Check if the complete message is available
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        // Read the message bytes
        byte[] msgData = new byte[length];
        in.readBytes(msgData);

        try {
            // Parse the ISO message
            IsoMessage isoMsg = messageFactory.parseMessage(msgData, 0);
            if (isoMsg != null) {
                log.info("Decoded ISO message: {}", isoMsg.debugString());
                out.add(isoMsg);
            } else {
                log.error("Failed to decode ISO message");
            }
        } catch (Exception e) {
            log.error("Error decoding ISO message", e);
        }
    }
}
