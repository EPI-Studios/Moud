package com.meekdev.moud.core.nav;

public record BlockShape(float[] boxes, Liquid liquid, boolean climbable, String name) {

    public enum Liquid { NONE, WATER, LAVA }

    private static final int BOX_STRIDE = 6;

    public static final BlockShape AIR = new BlockShape(new float[0], Liquid.NONE, false, null);
    public static final BlockShape SOLID = new BlockShape(new float[] {0, 0, 0, 1, 1, 1}, Liquid.NONE, false, null);
    public static final BlockShape WATER = new BlockShape(new float[0], Liquid.WATER, false, "water");
    public static final BlockShape LAVA = new BlockShape(new float[0], Liquid.LAVA, false, "lava");

    public static BlockShape of(double... boxes) {
        return of(null, Liquid.NONE, false, boxes);
    }

    public static BlockShape of(String name, Liquid liquid, boolean climbable, double... boxes) {
        float[] narrowed = new float[boxes.length];
        for (int i = 0; i < boxes.length; i++) narrowed[i] = (float) boxes[i];
        return new BlockShape(narrowed, liquid, climbable, name);
    }

    public boolean empty() {
        return boxes.length == 0;
    }

    public int boxCount() {
        return boxes.length / BOX_STRIDE;
    }

    public float minX(int box) {
        return boxes[box * BOX_STRIDE];
    }

    public float minY(int box) {
        return boxes[box * BOX_STRIDE + 1];
    }

    public float minZ(int box) {
        return boxes[box * BOX_STRIDE + 2];
    }

    public float maxX(int box) {
        return boxes[box * BOX_STRIDE + 3];
    }

    public float maxY(int box) {
        return boxes[box * BOX_STRIDE + 4];
    }

    public float maxZ(int box) {
        return boxes[box * BOX_STRIDE + 5];
    }
}
