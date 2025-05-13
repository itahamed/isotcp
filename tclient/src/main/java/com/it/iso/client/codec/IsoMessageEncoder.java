package com.it.iso.client.codec;

import java.nio.ByteBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.solab.iso8583.IsoMessage;

/**
 * Netty encoder for ISO8583 messages
 */
public class IsoMessageEncoder extends MessageToByteEncoder<IsoMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, IsoMessage msg, ByteBuf out) throws Exception {
        byte[] data = msg.writeData();

        // Add length header for framing (2 bytes)
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 2);
        buffer.putShort((short) data.length);
        buffer.put(data);
        buffer.flip();

        out.writeBytes(buffer);
    }
}