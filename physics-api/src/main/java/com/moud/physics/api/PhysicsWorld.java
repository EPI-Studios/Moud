package com.moud.physics.api;

import java.util.List;
import java.util.Optional;

public interface PhysicsWorld extends AutoCloseable {

    BodyHandle addStatic(ShapeDesc shape, Transform xform, CollisionGroups groups);
    BodyHandle addDynamic(ShapeDesc shape, Transform xform, DynamicProps props, CollisionGroups groups);
    BodyHandle addKinematic(ShapeDesc shape, Transform xform, CollisionGroups groups);
    BodyHandle addArea(ShapeDesc shape, Transform xform, CollisionGroups groups);
    void remove(BodyHandle h);

    void setTransform(BodyHandle h, Transform xform);
    Transform getTransform(BodyHandle h);
    void applyForce(BodyHandle h, Vec3 force);
    void applyImpulse(BodyHandle h, Vec3 impulse);
    void setLinearVelocity(BodyHandle h, Vec3 v);
    Vec3  getLinearVelocity(BodyHandle h);
    void sleep(BodyHandle h);
    void wake(BodyHandle h);

    JointHandle addFixedJoint(BodyHandle a, BodyHandle b, Transform localA, Transform localB, boolean contactsEnabled);
    JointHandle addSphericalJoint(BodyHandle a, BodyHandle b, Vec3 localAnchorA, Vec3 localAnchorB, boolean contactsEnabled);
    void removeJoint(JointHandle h);

    Optional<RaycastHit>   raycast(Vec3 origin, Vec3 dir, float maxDist, QueryFilter f);
    Optional<ShapeCastHit> shapeCast(ShapeDesc s, Transform from, Vec3 dir, float maxDist, QueryFilter f);
    long[]                 overlap(ShapeDesc s, Transform xform, QueryFilter f);

    List<ContactEvent> drainContactEvents();
    List<AreaEvent>    drainAreaEvents();

    void setGravity(Vec3 gravity);
    void step(float dt);

    @Override void close();
}
