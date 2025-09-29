package com.moud.server.raycast;

import com.moud.api.math.Vector3;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

public record RaycastResult(
        boolean didHit,
        Vector3 position,
        @Nullable Vector3 normal,
        @Nullable Entity entity,
        @Nullable Block block,
        double distance
) {
    public static RaycastResult noHit(Vector3 endPosition, double distance) {
        return new RaycastResult(false, endPosition, null, null, null, distance);
    }
}