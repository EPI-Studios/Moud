package com.moud.client.fabric.physics;

import net.minecraft.util.math.Box;

public interface CollisionShape {
    int layerBits();

    int maskBits();

    Box worldAabb();

    double[] computeMtv(double ax, double ay, double az, double fw, double fh, double fd);
}
