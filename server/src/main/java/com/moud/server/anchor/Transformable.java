package com.moud.server.anchor;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

/**
 * Interface for objects that have a position, rotation, and scale in 3D space.
 * Used by {@link AnchorBehavior} to update world transforms when anchored to other objects.
 */
public interface Transformable {

    /**
     * Gets the current world position.
     */
    Vector3 getPosition();

    /**
     * Gets the current world rotation.
     */
    Quaternion getRotation();

    /**
     * Gets the current world scale.
     */
    Vector3 getScale();

    /**
     * Applies a world transform computed from anchor tracking.
     *
     * @param position the new world position
     * @param rotation the new world rotation
     * @param scale the new world scale
     */
    void applyAnchoredTransform(Vector3 position, Quaternion rotation, Vector3 scale);
}
