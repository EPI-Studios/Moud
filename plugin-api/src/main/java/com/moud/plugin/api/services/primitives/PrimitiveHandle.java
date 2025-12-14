package com.moud.plugin.api.services.primitives;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;

import java.util.List;

/**
 * Handle to a primitive mesh for manipulation.
 */
public interface PrimitiveHandle extends AutoCloseable {
    /**
     * Gets the unique ID of this primitive.
     */
    long getId();

    /**
     * Gets the type of this primitive.
     */
    PrimitiveType getType();

    /**
     * Gets the group ID this primitive belongs to, or null.
     */
    String getGroupId();

    /**
     * Gets the current position.
     */
    Vector3 getPosition();

    /**
     * Gets the current rotation.
     */
    Quaternion getRotation();

    /**
     * Gets the current scale.
     */
    Vector3 getScale();

    /**
     * Sets the position.
     */
    void setPosition(Vector3 position);

    /**
     * Sets the rotation.
     */
    void setRotation(Quaternion rotation);

    /**
     * Sets the scale.
     */
    void setScale(Vector3 scale);

    /**
     * Sets position, rotation, and scale in one call.
     */
    void setTransform(Vector3 position, Quaternion rotation, Vector3 scale);

    /**
     * Orients the primitive to point from one position to another.
     * Useful for bone/limb rendering.
     *
     * @param from Start position
     * @param to End position
     * @param thickness Scale factor for width/depth
     */
    void setFromTo(Vector3 from, Vector3 to, float thickness);

    /**
     * Sets the color (RGB, 0-1 range).
     */
    void setColor(float r, float g, float b);

    /**
     * Sets the color with alpha (RGBA, 0-1 range).
     */
    void setColor(float r, float g, float b, float a);

    /**
     * Sets whether the primitive is unlit (no shading).
     */
    void setUnlit(boolean unlit);

    /**
     * Sets whether the primitive renders through blocks.
     */
    void setRenderThroughBlocks(boolean enabled);

    /**
     * Sets whether both sides of faces are rendered.
     */
    void setDoubleSided(boolean doubleSided);

    /**
     * Sets a texture path for the primitive.
     */
    void setTexture(String texturePath);

    /**
     * For LINE and LINE_STRIP types, updates the vertices.
     */
    void setVertices(List<Vector3> vertices);

    /**
     * Removes this primitive.
     */
    void remove();

    /**
     * Checks if this primitive has been removed.
     */
    boolean isRemoved();

    @Override
    default void close() {
        remove();
    }
}
