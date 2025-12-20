package com.moud.server.network;

import com.moud.network.limits.NetworkLimits;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InboundPacketByteBufferTest {

    @Test
    void rejectsByteArraysLargerThanRemainingBytes() {
        InboundPacketByteBuffer buffer = new InboundPacketByteBuffer(new byte[]{0x64}); // VarInt(100), no payload bytes
        assertThrows(IllegalArgumentException.class, buffer::readByteArray);
    }

    @Test
    void rejectsByteArraysLargerThanConfiguredLimit() {
        byte[] encodedLength = encodeVarInt(NetworkLimits.MAX_BYTE_ARRAY_BYTES + 1);
        InboundPacketByteBuffer buffer = new InboundPacketByteBuffer(encodedLength);
        assertThrows(IllegalArgumentException.class, buffer::readByteArray);
    }

    @Test
    void rejectsStringsLargerThanRemainingBytes() {
        InboundPacketByteBuffer buffer = new InboundPacketByteBuffer(new byte[]{0x01}); // VarInt(1), no string bytes
        assertThrows(IllegalArgumentException.class, buffer::readString);
    }

    private static byte[] encodeVarInt(int value) {
        byte[] tmp = new byte[5];
        int index = 0;

        int remaining = value;
        do {
            byte temp = (byte) (remaining & 0b0111_1111);
            remaining >>>= 7;
            if (remaining != 0) {
                temp |= (byte) 0b1000_0000;
            }
            tmp[index++] = temp;
        } while (remaining != 0);

        return Arrays.copyOf(tmp, index);
    }
}

