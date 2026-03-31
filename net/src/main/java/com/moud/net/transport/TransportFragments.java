package com.moud.net.transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportFragments {
    private static final byte MAGIC = 0x7F;

    private TransportFragments() {
    }

    public static List<byte[]> encode(byte[] frame, int maxFragmentBytes, int messageId) {
        Objects.requireNonNull(frame, "frame");
        if (maxFragmentBytes <= 0) {
            throw new IllegalArgumentException("maxFragmentBytes must be > 0");
        }
        if (frame.length <= maxFragmentBytes) {
            return List.of(frame);
        }

        int headerBytes = 1 + 4 + 4 + 4 + 4;
        int chunkBytes = maxFragmentBytes - headerBytes;
        if (chunkBytes <= 0) {
            throw new IllegalArgumentException("maxFragmentBytes too small: " + maxFragmentBytes);
        }

        int total = (frame.length + chunkBytes - 1) / chunkBytes;
        ArrayList<byte[]> out = new ArrayList<>(total);
        for (int index = 0, off = 0; off < frame.length; index++) {
            int len = Math.min(chunkBytes, frame.length - off);
            ByteBuffer buf = ByteBuffer.allocate(headerBytes + len);
            buf.put(MAGIC);
            buf.putInt(messageId);
            buf.putInt(index);
            buf.putInt(total);
            buf.putInt(len);
            buf.put(frame, off, len);
            out.add(buf.array());
            off += len;
        }
        return List.copyOf(out);
    }

    public static byte[] accept(Map<Integer, PartialMessage> partials, byte[] packet) {
        Objects.requireNonNull(partials, "partials");
        Objects.requireNonNull(packet, "packet");
        if (packet.length == 0 || packet[0] != MAGIC) {
            return packet;
        }
        if (packet.length < 17) {
            return null;
        }

        ByteBuffer in = ByteBuffer.wrap(packet);
        in.get();
        int messageId = in.getInt();
        int index = in.getInt();
        int total = in.getInt();
        int len = in.getInt();
        if (messageId == 0 || total <= 0 || index < 0 || index >= total || len < 0 || in.remaining() != len) {
            partials.remove(messageId);
            return null;
        }

        PartialMessage partial = partials.computeIfAbsent(messageId, ignored -> new PartialMessage(total));
        if (partial.total != total) {
            partials.remove(messageId);
            return null;
        }

        byte[] chunk = new byte[len];
        in.get(chunk);
        if (!partial.put(index, chunk)) {
            partials.remove(messageId);
            return null;
        }
        if (!partial.complete()) {
            return null;
        }

        partials.remove(messageId);
        return partial.join();
    }

    public static Map<Integer, PartialMessage> newPartialStore() {
        return new ConcurrentHashMap<>();
    }

    public static final class PartialMessage {
        private final int total;
        private final byte[][] chunks;
        private int received;

        PartialMessage(int total) {
            this.total = total;
            this.chunks = new byte[total][];
        }

        synchronized boolean put(int index, byte[] chunk) {
            if (chunk == null || index < 0 || index >= total) {
                return false;
            }
            if (chunks[index] != null) {
                return false;
            }
            chunks[index] = chunk;
            received++;
            return true;
        }

        synchronized boolean complete() {
            return received == total;
        }

        synchronized byte[] join() {
            int size = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    return null;
                }
                size += chunk.length;
            }
            byte[] out = new byte[size];
            int off = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, out, off, chunk.length);
                off += chunk.length;
            }
            return out;
        }
    }
}
