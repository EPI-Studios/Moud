package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;

public final class Movers {

    public static final double ARROW = 1.2;
    private static final double RESPONSE = 0.3;
    private static final double MIN_RESPONSIVENESS = 5;
    private static final double MAX_RESPONSIVENESS = 200;
    private static final double NO_TURN = 1.0e-9;
    private static final double NO_ARROW_SQ = 1.0e-12;
    private static final double NEARLY_UPRIGHT = 0.9;

    private Movers() {}

    public static boolean needsSecond(Constraint mover) {
        return switch (mover) {
            case AlignPosition align -> align.mode == AlignmentMode.TWO_ATTACHMENT;
            case AlignOrientation align -> align.mode == AlignmentMode.TWO_ATTACHMENT;
            case LinearVelocity velocity -> velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1;
            case AngularVelocity velocity -> velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1;
            case VectorForce force -> force.relativeTo == ActuatorRelativeTo.ATTACHMENT1;
            case Torque torque -> torque.relativeTo == ActuatorRelativeTo.ATTACHMENT1;
            default -> false;
        };
    }

    public static boolean pushesSecond(Constraint mover) {
        return switch (mover) {
            case AlignPosition align -> align.mode == AlignmentMode.TWO_ATTACHMENT && align.reactionForceEnabled;
            case AlignOrientation align -> align.mode == AlignmentMode.TWO_ATTACHMENT && align.reactionTorqueEnabled;
            case LinearVelocity velocity -> velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1 && velocity.reactionForceEnabled;
            case AngularVelocity velocity -> velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1 && velocity.reactionTorqueEnabled;
            default -> false;
        };
    }

    public static Quat frame(ActuatorRelativeTo relativeTo, CFrame at0, CFrame at1) {
        return switch (relativeTo) {
            case WORLD -> Quat.IDENTITY;
            case ATTACHMENT0 -> at0.rotation();
            case ATTACHMENT1 -> at1 == null ? null : at1.rotation();
        };
    }

    public static double rate(double responsiveness, boolean rigid, double dt) {
        double oneStep = 1 / dt;
        if (rigid) return oneStep;
        double clamped = Math.clamp(responsiveness, MIN_RESPONSIVENESS, MAX_RESPONSIVENESS);
        return Math.min(oneStep, clamped * RESPONSE);
    }

    public static Vector3 capped(Vector3 vector, double most) {
        double length = vector.length();
        double limit = Math.max(0, most);
        return length > limit ? vector.mul(limit / length) : vector;
    }

    public static Vector3 goal(AlignPosition align, CFrame at1) {
        if (align.mode == AlignmentMode.ONE_ATTACHMENT) return align.position;
        return at1 == null ? null : at1.position();
    }

    public static Quat goal(AlignOrientation align, CFrame at1) {
        if (align.mode == AlignmentMode.ONE_ATTACHMENT) return align.cframe.rotation();
        return at1 == null ? null : at1.rotation();
    }

    public static Vector3 pull(AlignPosition align, Vector3 gap, Vector3 velocity, Vector3 gravity, double mass, double dt) {
        boolean rigid = align.rigidityEnabled;
        Vector3 wanted = gap.mul(rate(align.responsiveness, rigid, dt));
        if (!rigid) wanted = capped(wanted, align.maxVelocity);

        Vector3 acceleration = wanted.sub(velocity).mul(1 / dt).sub(gravity);
        Vector3 force = acceleration.mul(mass);
        return rigid ? force : capped(force, align.maxForce);
    }

    public static Vector3 turn(AlignOrientation align, Quat now, Quat goal, Vector3 spin, Inertia inertia, double dt) {
        boolean rigid = align.rigidityEnabled;
        Vector3 primary = now.rotate(Vector3.RIGHT);
        Vector3 gap = align.primaryAxisOnly ? swing(primary, goal.rotate(Vector3.RIGHT)) : gap(now, goal);
        Vector3 wanted = gap.mul(rate(align.responsiveness, rigid, dt));
        if (!rigid) wanted = capped(wanted, align.maxAngularVelocity);

        Vector3 change = wanted.sub(spin);
        if (align.primaryAxisOnly) change = flattened(change, primary);
        Vector3 torque = inertia.times(change.mul(1 / dt));
        return rigid ? torque : capped(torque, align.maxTorque);
    }

    public static Vector3 hold(LinearVelocity velocity, Quat frame, Vector3 base, Vector3 now, Vector3 gravity, double mass, double dt) {
        Vector3 drift = base.sub(now).sub(gravity.mul(dt));
        Vector3 change = switch (velocity.velocityConstraintMode) {
            case VECTOR -> frame.rotate(velocity.vectorVelocity).add(drift);
            case LINE -> {
                Vector3 line = frame.rotate(velocity.lineDirection).normalize();
                yield line.mul(velocity.lineVelocity + line.dot(drift));
            }
            case PLANE -> {
                Vector3 first = frame.rotate(velocity.primaryTangentAxis).normalize();
                Vector3 second = flattened(frame.rotate(velocity.secondaryTangentAxis), first).normalize();
                Vector3 alongFirst = first.mul(velocity.planeVelocity.x() + first.dot(drift));
                Vector3 alongSecond = second.mul(velocity.planeVelocity.y() + second.dot(drift));
                yield alongFirst.add(alongSecond);
            }
        };
        Vector3 force = change.mul(mass / dt);
        return velocity.forceLimitsEnabled ? capped(force, velocity.maxForce) : force;
    }

    public static Vector3 spin(AngularVelocity velocity, Quat frame, Vector3 base, Vector3 now, Inertia inertia, double dt) {
        Vector3 wanted = frame.rotate(velocity.angularVelocity).add(base);
        Vector3 change = wanted.sub(now);
        Vector3 torque = inertia.times(change.mul(1 / dt));
        return capped(torque, velocity.maxTorque);
    }

    public static Vector3 gap(Quat from, Quat to) {
        Quat between = to.mul(from.inverse()).normalize();
        Vector3 axis = new Vector3(between.x(), between.y(), between.z());
        double cosine = between.w();
        if (cosine < 0) {
            axis = axis.neg();
            cosine = -cosine;
        }

        double sine = axis.length();
        if (sine < NO_TURN) return Vector3.ZERO;
        double angle = 2 * Math.atan2(sine, cosine);
        return axis.mul(angle / sine);
    }

    public static Vector3 swing(Vector3 from, Vector3 to) {
        Vector3 start = from.normalize();
        Vector3 end = to.normalize();
        Vector3 axis = start.cross(end);
        double sine = axis.length();
        double cosine = start.dot(end);
        if (sine < NO_TURN) return cosine > 0 ? Vector3.ZERO : perpendicular(start).mul(Math.PI);
        double angle = Math.atan2(sine, cosine);
        return axis.mul(angle / sine);
    }

    public static Vector3 arrow(Constraint mover, CFrame at0, CFrame at1) {
        return switch (mover) {
            case AlignPosition align -> {
                Vector3 goal = goal(align, at1);
                yield goal == null ? null : goal.sub(at0.position());
            }
            case AlignOrientation align -> {
                Quat goal = goal(align, at1);
                yield goal == null ? null : goal.rotate(Vector3.RIGHT).mul(ARROW);
            }
            case LinearVelocity velocity -> pointed(frame(velocity.relativeTo, at0, at1), shown(velocity));
            case AngularVelocity velocity -> pointed(frame(velocity.relativeTo, at0, at1), velocity.angularVelocity);
            case VectorForce force -> pointed(frame(force.relativeTo, at0, at1), force.force);
            case Torque torque -> pointed(frame(torque.relativeTo, at0, at1), torque.torque);
            default -> null;
        };
    }

    private static Vector3 flattened(Vector3 vector, Vector3 normal) {
        return vector.sub(normal.mul(vector.dot(normal)));
    }

    private static Vector3 perpendicular(Vector3 direction) {
        Vector3 other = Math.abs(direction.y()) < NEARLY_UPRIGHT ? Vector3.UP : Vector3.RIGHT;
        return direction.cross(other).normalize();
    }

    private static Vector3 shown(LinearVelocity velocity) {
        return switch (velocity.velocityConstraintMode) {
            case VECTOR -> velocity.vectorVelocity;
            case LINE -> velocity.lineDirection.normalize().mul(velocity.lineVelocity);
            case PLANE -> {
                Vector3 first = velocity.primaryTangentAxis.normalize().mul(velocity.planeVelocity.x());
                Vector3 second = velocity.secondaryTangentAxis.normalize().mul(velocity.planeVelocity.y());
                yield first.add(second);
            }
        };
    }

    private static Vector3 pointed(Quat frame, Vector3 local) {
        if (frame == null || local.lengthSq() < NO_ARROW_SQ) return null;
        return frame.rotate(local).normalize().mul(ARROW);
    }
}
