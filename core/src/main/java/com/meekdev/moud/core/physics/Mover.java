package com.meekdev.moud.core.physics;

public sealed interface Mover permits AlignPosition, AlignOrientation, LinearVelocity, AngularVelocity, VectorForce, Torque {
}
