package com.meekdev.moud.core.math;

// a place on a surface, per axis as a fraction of the parent plus a number of pixels
public record UDim2(double xScale, double xOffset, double yScale, double yOffset) {

    public static final UDim2 ZERO = new UDim2(0, 0, 0, 0);

    public static UDim2 fromScale(double x, double y) {
        return new UDim2(x, 0, y, 0);
    }

    public static UDim2 fromOffset(double x, double y) {
        return new UDim2(0, x, 0, y);
    }

    public UDim2 add(UDim2 o) {
        return new UDim2(xScale + o.xScale, xOffset + o.xOffset, yScale + o.yScale, yOffset + o.yOffset);
    }

    public UDim2 sub(UDim2 o) {
        return new UDim2(xScale - o.xScale, xOffset - o.xOffset, yScale - o.yScale, yOffset - o.yOffset);
    }

    public UDim2 lerp(UDim2 to, double t) {
        return new UDim2(xScale + (to.xScale - xScale) * t, xOffset + (to.xOffset - xOffset) * t,
                yScale + (to.yScale - yScale) * t, yOffset + (to.yOffset - yOffset) * t);
    }

    public double x(double parent) {
        return xScale * parent + xOffset;
    }

    public double y(double parent) {
        return yScale * parent + yOffset;
    }
}
