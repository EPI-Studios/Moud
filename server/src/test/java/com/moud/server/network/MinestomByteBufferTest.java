package com.moud.server.network;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MinestomByteBufferTest {

    @Test
    void handlesLargeByteArrays() {
        MinestomByteBuffer writer = new MinestomByteBuffer();
        byte[] payload = new byte[16 * 1024];
        new Random(42).nextBytes(payload);

        writer.writeByteArray(payload);
        byte[] serialized = writer.toByteArray();

        MinestomByteBuffer reader = new MinestomByteBuffer(serialized);
        byte[] decoded = reader.readByteArray();

        assertArrayEquals(payload, decoded, "Decoded payload should match original data");
    }
}
