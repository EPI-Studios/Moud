package com.moud.client.fabric.physics.rapier;

import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.CollisionGroups;
import com.moud.physics.api.QueryFilter;
import com.moud.physics.api.RaycastHit;
import com.moud.physics.api.ShapeCastHit;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;
import com.moud.physics.rapier.RapierCharacterController;
import com.moud.physics.rapier.RapierPhysicsWorld;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// thin wrapper around a RapierPhysicsWorld for the client side
//
// adds two things on top of plain RapierPhysicsWorld:
// 1. a cross-thread mutation queue - network-thread callers post() runnables
//    and the render thread drains them lazily before any rapier call
// 2. a lazily-created KinematicCharacterController shared between calls
//
// every public method auto-pumps the queue so forgetting to drain is impossible
public final class ClientStaticWorld implements AutoCloseable {

    private final RapierPhysicsWorld world = new RapierPhysicsWorld();
    private RapierCharacterController kcc;

    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    // cross-thread mutation entry - runs on the render thread
    public void post(Runnable mutation) {
        if (mutation != null) pending.offer(mutation);
    }

    // render thread only - drains queued mutations
    private void pump() {
        Runnable r;
        while ((r = pending.poll()) != null) {
            try { r.run(); } catch (RuntimeException ignored) {}
        }
    }

    private RapierCharacterController kcc() {
        if (kcc == null) kcc = new RapierCharacterController(world);
        return kcc;
    }

    // ---- bodies ------------------------------------------------------------

    public BodyHandle addStatic(ShapeDesc shape, Transform x, CollisionGroups g) {
        pump();
        return world.addStatic(shape, x, g);
    }

    public BodyHandle addKinematic(ShapeDesc shape, Transform x, CollisionGroups g) {
        pump();
        return world.addKinematic(shape, x, g);
    }

    public void setTransform(BodyHandle h, Transform x) {
        pump();
        world.setTransform(h, x);
    }

    public void remove(BodyHandle h) {
        pump();
        world.remove(h);
    }

    // ---- queries -----------------------------------------------------------

    public Optional<RaycastHit> raycast(Vec3 o, Vec3 d, float maxD, QueryFilter f) {
        pump();
        return world.raycast(o, d, maxD, f);
    }

    public Optional<ShapeCastHit> shapeCast(ShapeDesc s, Transform from, Vec3 dir, float maxD, QueryFilter f) {
        pump();
        return world.shapeCast(s, from, dir, maxD, f);
    }

    // shape-based KCC move - used by the player capsule sweep
    public RapierCharacterController.Result moveCharacter(ShapeDesc shape, Transform pose,
                                                          Vec3 desired, float dt) {
        pump();
        return kcc().moveShape(shape, pose, desired, dt);
    }

    // some rapier internals (broad-phase index) only refresh on a step - call
    // this after big collider changes if a query is about to follow
    public void refreshQueries() {
        pump();
        world.step(0f);
    }

    @Override
    public void close() {
        if (kcc != null) {
            kcc.close();
            kcc = null;
        }
        world.close();
    }
}
