package com.meekdev.moud.script.api;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;

public interface PartPhysicsRef {

    void applyImpulse(Part part, Vector3 impulse, Vector3 at);

    void applyAngularImpulse(Part part, Vector3 impulse);

    void setVelocity(Part part, Vector3 velocity);

    void setAngularVelocity(Part part, Vector3 velocity);

    Vector3 velocityAt(Part part, Vector3 position);

    double mass(Part part);
}
