package com.meekdev.moud.core.nav;

public record BlockShape(float[] boxes) {

    public static final BlockShape AIR = new BlockShape(new float[0]);
    public static final BlockShape SOLID = new BlockShape(new float[] {0, 0, 0, 1, 1, 1});

    public static BlockShape of(double... boxes) {
        float[] out = new float[boxes.length];
        for (int i = 0; i < boxes.length; i++) out[i] = (float) boxes[i];
        return new BlockShape(out);
    }

    public boolean empty() {
        return boxes.length == 0;
    }
}
