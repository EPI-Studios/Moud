package com.moud.core.physics;


import java.util.List;
import java.util.Optional;

/**
 * General-purpose physics world abstraction. Implementations (e.g. Jolt) are in the server module.
 *
 * <p>Static bodies represent solid, non-moving geometry. Dynamic bodies are simulated under gravity.
 * Query methods (raycast, overlapSphere) work against all bodies in the world.
 */
public interface PhysicsWorld extends AutoCloseable {

    /** Advance the simulation by {@code dt} seconds. */
    void step(float dt);

    /** Release all native resources held by this world. */
    @Override
    void close();

    /**
     * Add an immovable (static) body to the world.
     *
     * @param shape the collision geometry
     * @param x     world X of the body centre
     * @param y     world Y
     * @param z     world Z
     * @param rxDeg rotation around X axis (degrees)
     * @param ryDeg rotation around Y axis (degrees)
     * @param rzDeg rotation around Z axis (degrees)
     * @return handle to the created body, or {@link BodyHandle#INVALID} on failure
     */
    BodyHandle addStaticBody(CollisionShape shape, double x, double y, double z,
                             float rxDeg, float ryDeg, float rzDeg);

    /**
     * Add a simulated (dynamic) body to the world.
     *
     * @param mass kg; must be positive
     * @return handle to the created body, or {@link BodyHandle#INVALID} on failure
     */
    BodyHandle addDynamicBody(CollisionShape shape, double x, double y, double z,
                              float rxDeg, float ryDeg, float rzDeg, float mass);

    /** Remove and destroy the body identified by {@code handle}. No-op for invalid handles. */
    void removeBody(BodyHandle handle);

    /**
     * Cast a ray and return the closest hit, if any.
     *
     * @param ox      ray origin X
     * @param oy      ray origin Y
     * @param oz      ray origin Z
     * @param dx      ray direction X (need not be normalised)
     * @param dy      ray direction Y
     * @param dz      ray direction Z
     * @param maxDist maximum distance along the ray
     */
    Optional<RaycastResult> raycast(double ox, double oy, double oz,
                                    double dx, double dy, double dz,
                                    double maxDist);

    /**
     * Return all body handles whose shapes overlap a sphere of the given {@code radius}
     * centred at {@code (x, y, z)}.
     */
    List<BodyHandle> overlapSphere(double x, double y, double z, double radius);
}
