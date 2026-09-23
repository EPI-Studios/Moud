package com.meekdev.moud.core.image;

import java.nio.charset.StandardCharsets;

// reads a Radiance .hdr file: a text header, a resolution line, then RGBE pixels, flat or run-length
// encoded per scanline. the result is linear rgb, top row first
public final class Radiance {

    public record Image(int width, int height, float[] rgb) {}

    private Radiance() {}

    public static Image decode(byte[] bytes) {
        int at = 0;
        String first = null;
        boolean rgbe = true;
        while (true) {
            int end = lineEnd(bytes, at);
            String line = new String(bytes, at, end - at, StandardCharsets.US_ASCII).trim();
            at = end + 1;
            if (first == null) {
                first = line;
                if (!line.startsWith("#?")) throw new IllegalArgumentException("not a Radiance .hdr file");
                continue;
            }
            if (line.isEmpty()) break;
            if (line.startsWith("FORMAT=")) rgbe = line.equals("FORMAT=32-bit_rle_rgbe");
            if (at >= bytes.length) throw new IllegalArgumentException("the .hdr header never ends");
        }
        if (!rgbe) throw new IllegalArgumentException("only 32-bit_rle_rgbe .hdr files are read");
        int end = lineEnd(bytes, at);
        String[] size = new String(bytes, at, end - at, StandardCharsets.US_ASCII).trim().split("\\s+");
        at = end + 1;
        if (size.length != 4 || !size[0].equals("-Y") || !size[2].equals("+X")) {
            throw new IllegalArgumentException("only top-down, left-to-right .hdr files are read");
        }
        int height = Integer.parseInt(size[1]);
        int width = Integer.parseInt(size[3]);
        if (width <= 0 || height <= 0 || (long) width * height > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("a .hdr of " + width + " by " + height + " is not read");
        }

        float[] rgb = new float[width * height * 3];
        byte[] row = new byte[width * 4];
        for (int y = 0; y < height; y++) {
            at = scanline(bytes, at, row, width);
            int out = y * width * 3;
            for (int x = 0; x < width; x++) {
                int e = row[x * 4 + 3] & 0xFF;
                if (e == 0) {
                    out += 3;
                    continue;
                }
                float scale = (float) Math.scalb(1.0, e - 136);
                rgb[out++] = (row[x * 4] & 0xFF) * scale;
                rgb[out++] = (row[x * 4 + 1] & 0xFF) * scale;
                rgb[out++] = (row[x * 4 + 2] & 0xFF) * scale;
            }
        }
        return new Image(width, height, rgb);
    }

    private static int scanline(byte[] bytes, int at, byte[] row, int width) {
        need(bytes, at, 4);
        boolean encoded = width >= 8 && width < 32768 && bytes[at] == 2 && bytes[at + 1] == 2 && (bytes[at + 2] & 0x80) == 0;
        if (!encoded) {
            need(bytes, at, width * 4);
            System.arraycopy(bytes, at, row, 0, width * 4);
            return at + width * 4;
        }
        if ((((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF)) != width) throw new IllegalArgumentException("a .hdr scanline has the wrong width");
        at += 4;
        for (int channel = 0; channel < 4; channel++) {
            int x = 0;
            while (x < width) {
                need(bytes, at, 1);
                int count = bytes[at++] & 0xFF;
                if (count > 128) {
                    count -= 128;
                    if (count > width - x) throw new IllegalArgumentException("a .hdr run overflows its scanline");
                    need(bytes, at, 1);
                    byte value = bytes[at++];
                    for (int n = 0; n < count; n++) row[(x++) * 4 + channel] = value;
                } else {
                    if (count == 0 || count > width - x) throw new IllegalArgumentException("a .hdr run overflows its scanline");
                    need(bytes, at, count);
                    for (int n = 0; n < count; n++) row[(x++) * 4 + channel] = bytes[at++];
                }
            }
        }
        return at;
    }

    private static int lineEnd(byte[] bytes, int from) {
        for (int at = from; at < bytes.length; at++) {
            if (bytes[at] == '\n') return at;
        }
        throw new IllegalArgumentException("the .hdr file ends inside its header");
    }

    private static void need(byte[] bytes, int at, int count) {
        if (at + count > bytes.length) throw new IllegalArgumentException("the .hdr file is cut short");
    }
}
