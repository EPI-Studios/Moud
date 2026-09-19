package com.meekdev.moud.core.effect;

import java.util.Arrays;

public final class CeilingGrid implements PrecipitationField.Ceiling {

    private final int radius;
    private final int size;
    private final double[] tops;
    private int originX;
    private int originZ;

    public CeilingGrid(int radius) {
        this.radius = Math.max(1, radius);
        this.size = this.radius * 2 + 1;
        this.tops = new double[size * size];
        Arrays.fill(tops, Double.NEGATIVE_INFINITY);
    }

    public int radius() {
        return radius;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public void reset(int centreX, int centreZ) {
        originX = centreX - radius;
        originZ = centreZ - radius;
        Arrays.fill(tops, Double.NEGATIVE_INFINITY);
    }

    public void raise(int x, int z, double top) {
        int column = x - originX;
        int row = z - originZ;
        if (column < 0 || row < 0 || column >= size || row >= size) return;
        int at = row * size + column;
        if (top > tops[at]) tops[at] = top;
    }

    public void raise(double minX, double minZ, double maxX, double maxZ, double top) {
        int fromX = Math.max(originX, (int) Math.floor(minX));
        int toX = Math.min(originX + size - 1, (int) Math.floor(maxX));
        int fromZ = Math.max(originZ, (int) Math.floor(minZ));
        int toZ = Math.min(originZ + size - 1, (int) Math.floor(maxZ));
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = fromX; x <= toX; x++) raise(x, z, top);
        }
    }

    public boolean covers(double minX, double minZ, double maxX, double maxZ) {
        return maxX >= originX && maxZ >= originZ && minX < originX + size && minZ < originZ + size;
    }

    @Override
    public double top(double x, double z) {
        int column = (int) Math.floor(x) - originX;
        int row = (int) Math.floor(z) - originZ;
        if (column < 0 || row < 0 || column >= size || row >= size) return Double.NEGATIVE_INFINITY;
        return tops[row * size + column];
    }
}
