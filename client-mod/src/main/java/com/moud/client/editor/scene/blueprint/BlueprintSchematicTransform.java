package com.moud.client.editor.scene.blueprint;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class BlueprintSchematicTransform {
    private BlueprintSchematicTransform() {
    }

    public static int outSizeX(int sizeX, int sizeZ, int rotationSteps) {
        return (rotationSteps & 1) == 0 ? sizeX : sizeZ;
    }

    public static int outSizeZ(int sizeX, int sizeZ, int rotationSteps) {
        return (rotationSteps & 1) == 0 ? sizeZ : sizeX;
    }

    public static Vec3d transformPosition(Blueprint.BlockVolume blocks, double x, double y, double z,
                                          int rotationSteps, boolean mirrorX, boolean mirrorZ) {
        if (blocks == null) {
            return new Vec3d(x, y, z);
        }
        double sx = Math.max(1, blocks.sizeX);
        double sz = Math.max(1, blocks.sizeZ);
        int steps = rotationSteps & 3;
        double outX = outSizeX((int) sx, (int) sz, steps);
        double outZ = outSizeZ((int) sx, (int) sz, steps);

        double px = x;
        double pz = z;
        if (mirrorX) {
            px = sx - px;
        }
        if (mirrorZ) {
            pz = sz - pz;
        }

        double tx;
        double tz;
        switch (steps) {
            case 1 -> { // clockwise 90
                tx = outX - pz;
                tz = px;
            }
            case 2 -> { // 180
                tx = outX - px;
                tz = outZ - pz;
            }
            case 3 -> { // clockwise 270
                tx = pz;
                tz = outZ - px;
            }
            default -> { // 0
                tx = px;
                tz = pz;
            }
        }
        return new Vec3d(tx, y, tz);
    }


}

