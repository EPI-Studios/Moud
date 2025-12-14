package com.moud.plugin.api.services;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.services.primitives.PrimitiveHandle;
import com.moud.plugin.api.services.primitives.PrimitiveMaterial;
import com.moud.plugin.api.services.primitives.PrimitiveType;

import java.util.Collection;
import java.util.List;

/**
 * Service for creating and managing primitive mesh rendering.
 */
public interface PrimitiveService {

    /**
     * Creates a cube primitive.
     *
     * @param position Center position
     * @param scale Size in each dimension
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createCube(Vector3 position, Vector3 scale, PrimitiveMaterial material);

    /**
     * Creates a cube with default white material.
     */
    default PrimitiveHandle createCube(Vector3 position, Vector3 scale) {
        return createCube(position, scale, PrimitiveMaterial.white());
    }

    /**
     * Creates a cube with uniform scale.
     */
    default PrimitiveHandle createCube(Vector3 position, float size, PrimitiveMaterial material) {
        return createCube(position, new Vector3(size, size, size), material);
    }

    /**
     * Creates a sphere primitive.
     *
     * @param position Center position
     * @param radius Sphere radius
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createSphere(Vector3 position, float radius, PrimitiveMaterial material);

    /**
     * Creates a sphere with default white material.
     */
    default PrimitiveHandle createSphere(Vector3 position, float radius) {
        return createSphere(position, radius, PrimitiveMaterial.white());
    }

    /**
     * Creates a cylinder primitive.
     *
     * @param position Center position
     * @param radius Cylinder radius
     * @param height Cylinder height
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createCylinder(Vector3 position, float radius, float height, PrimitiveMaterial material);

    /**
     * Creates a capsule primitive (cylinder with hemispherical caps).
     *
     * @param position Center position
     * @param radius Capsule radius
     * @param height Total height including caps
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createCapsule(Vector3 position, float radius, float height, PrimitiveMaterial material);

    /**
     * Creates a cone primitive.
     *
     * @param position Base center position
     * @param radius Base radius
     * @param height Cone height
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createCone(Vector3 position, float radius, float height, PrimitiveMaterial material);

    /**
     * Creates a plane primitive.
     *
     * @param position Center position
     * @param width Width (X axis)
     * @param depth Depth (Z axis)
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createPlane(Vector3 position, float width, float depth, PrimitiveMaterial material);

    /**
     * Creates a single line segment.
     *
     * @param start Start position
     * @param end End position
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createLine(Vector3 start, Vector3 end, PrimitiveMaterial material);

    /**
     * Creates a line strip through multiple points.
     *
     * @param points List of positions to connect
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createLineStrip(List<Vector3> points, PrimitiveMaterial material);

    /**
     * Creates a bone-like shape between two points.
     * Useful for visualizing IK chains.
     *
     * @param from Start position (joint)
     * @param to End position (next joint)
     * @param thickness Width/depth of the bone
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    PrimitiveHandle createBone(Vector3 from, Vector3 to, float thickness, PrimitiveMaterial material);

    /**
     * Creates a bone with default thickness.
     */
    default PrimitiveHandle createBone(Vector3 from, Vector3 to, PrimitiveMaterial material) {
        return createBone(from, to, 0.1f, material);
    }

    /**
     * Creates a joint sphere at a position.
     *
     * @param position Joint position
     * @param radius Joint radius
     * @param material Material/color settings
     * @return Handle to the created primitive
     */
    default PrimitiveHandle createJoint(Vector3 position, float radius, PrimitiveMaterial material) {
        return createSphere(position, radius, material);
    }

    /**
     * Creates a primitive with full control over all parameters.
     *
     * @param type Primitive type
     * @param position Position
     * @param rotation Rotation
     * @param scale Scale
     * @param material Material settings
     * @param groupId Optional group ID for batch operations
     * @return Handle to the created primitive
     */
    PrimitiveHandle create(PrimitiveType type, Vector3 position, Quaternion rotation,
                           Vector3 scale, PrimitiveMaterial material, String groupId);

    /**
     * Creates a primitive with full control, no group.
     */
    default PrimitiveHandle create(PrimitiveType type, Vector3 position, Quaternion rotation,
                                   Vector3 scale, PrimitiveMaterial material) {
        return create(type, position, rotation, scale, material, null);
    }


    /**
     * Gets a primitive by ID.
     */
    PrimitiveHandle getPrimitive(long primitiveId);

    /**
     * Gets all primitives.
     */
    Collection<PrimitiveHandle> getAllPrimitives();

    /**
     * Gets all primitives in a group.
     */
    Collection<PrimitiveHandle> getPrimitivesInGroup(String groupId);

    /**
     * Removes a primitive by ID.
     */
    boolean removePrimitive(long primitiveId);

    /**
     * Removes all primitives in a group.
     */
    void removeGroup(String groupId);

    /**
     * Removes all primitives.
     */
    void removeAll();

    /**
     * Begins a batch operation. Changes are collected and sent together.
     * Call endBatch() to apply.
     */
    void beginBatch();

    /**
     * Ends a batch operation and sends all collected changes.
     */
    void endBatch();

    /**
     * Returns whether we're currently in a batch operation.
     */
    boolean isBatching();
}
