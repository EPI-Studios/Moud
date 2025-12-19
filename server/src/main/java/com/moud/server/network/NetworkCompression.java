package com.moud.server.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public final class NetworkCompression {
    private NetworkCompression() {
    }

    public static byte[] gzipFloatArray(float[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length * 4);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            byte[] bytes = new byte[data.length * 4];
            int idx = 0;
            for (float v : data) {
                int bits = Float.floatToIntBits(v);
                bytes[idx++] = (byte) (bits);
                bytes[idx++] = (byte) (bits >>> 8);
                bytes[idx++] = (byte) (bits >>> 16);
                bytes[idx++] = (byte) (bits >>> 24);
            }
            gzip.write(bytes);
        }

        return baos.toByteArray();
    }

    public static byte[] gzipIntArray(int[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length * 4);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            byte[] bytes = new byte[data.length * 4];
            int idx = 0;
            for (int v : data) {
                bytes[idx++] = (byte) (v);
                bytes[idx++] = (byte) (v >>> 8);
                bytes[idx++] = (byte) (v >>> 16);
                bytes[idx++] = (byte) (v >>> 24);
            }
            gzip.write(bytes);
        }

        return baos.toByteArray();
    }
}

