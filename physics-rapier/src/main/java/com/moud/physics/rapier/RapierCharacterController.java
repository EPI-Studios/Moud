package com.moud.physics.rapier;

import com.moud.physics.api.BodyHandle;
import com.moud.physics.api.ShapeDesc;
import com.moud.physics.api.Transform;
import com.moud.physics.api.Vec3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

// rapiers built-in kinematic character controller
//
// drive a kinematic body (capsule usually) with a desired displacement and get
// back the corrected one - slope, autostep, slide and snap-to-ground are
// handled by rapier
//
// the body has to be added as kinematic on the same world this controller was
// built with
public final class RapierCharacterController implements AutoCloseable {

    private final RapierPhysicsWorld physWorld;
    private final MemorySegment world;
    private final MemorySegment ctl;

    public RapierCharacterController(RapierPhysicsWorld w) {
        this.physWorld = w;
        this.world = w.nativeHandle();
        this.ctl   = Rapier3D.charCtlNew(world);
    }

    // body-based move - for things you registered as kinematic in the world
    public Result move(BodyHandle body, Vec3 desired, float dt) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(16);
            Rapier3D.charCtlMove(ctl, world, body.id(),
                    desired.x(), desired.y(), desired.z(), dt, out);
            return decode(out);
        }
    }

    // shape-based move - when the moving thing isnt a registered body
    // (e.g. the vanilla MC player capsule on the client side)
    public Result moveShape(ShapeDesc shape, Transform pose, Vec3 desired, float dt) {
        long sh = physWorld.internShape(shape);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(16);
            Rapier3D.charCtlMoveShape(ctl, world, sh,
                    pose.pos().x(), pose.pos().y(), pose.pos().z(),
                    pose.rot().x(), pose.rot().y(), pose.rot().z(), pose.rot().w(),
                    desired.x(), desired.y(), desired.z(),
                    dt, out);
            return decode(out);
        }
    }

    private static Result decode(MemorySegment out) {
        float dx = out.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
        float dy = out.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        float dz = out.getAtIndex(ValueLayout.JAVA_FLOAT, 2);
        int grounded = out.get(ValueLayout.JAVA_INT, 12);
        return new Result(new Vec3(dx, dy, dz), grounded != 0);
    }

    @Override
    public void close() {
        Rapier3D.charCtlDrop(ctl);
    }

    // corrected motion plus whether the move ended on the ground
    public record Result(Vec3 corrected, boolean grounded) {}
}
