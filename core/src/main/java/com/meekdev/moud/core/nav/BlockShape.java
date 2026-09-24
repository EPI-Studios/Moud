package com.meekdev.moud.core.nav;

public record BlockShape(float[] boxes, Liquid liquid) {

    public enum Liquid { NONE, WATER, LAVA }

    public static final BlockShape AIR = new BlockShape(new float[0], Liquid.NONE);
    public static final BlockShape SOLID = new BlockShape(new float[] {0, 0, 0, 1, 1, 1}, Liquid.NONE);
    public static final BlockShape WATER = new BlockShape(new float[0], Liquid.WATER);
    public static final BlockShape LAVA = new BlockShape(new float[0], Liquid.LAVA);

    public static BlockShape of(double... boxes) {
        return of(Liquid.NONE, boxes);
    }

    public static BlockShape of(Liquid liquid, double... boxes) {
        float[] out = new float[boxes.length];
        for (int i = 0; i < boxes.length; i++) out[i] = (float) boxes[i];
        return new BlockShape(out, liquid);
    }

    public boolean empty() {
        return boxes.length == 0;
    }
}
