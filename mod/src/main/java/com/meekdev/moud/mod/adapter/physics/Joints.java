package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.B3DistanceJoint;
import com.meekdev.box3d.B3Joint;
import com.meekdev.box3d.B3PrismaticJoint;
import com.meekdev.box3d.B3RevoluteJoint;
import com.meekdev.box3d.B3SphericalJoint;
import com.meekdev.box3d.B3World;
import com.meekdev.box3d.Vec3;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.physics.BallSocketConstraint;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.HingeConstraint;
import com.meekdev.moud.core.physics.PrismaticConstraint;
import com.meekdev.moud.core.physics.RodConstraint;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.SpringConstraint;
import com.meekdev.moud.core.physics.WeldConstraint;
import com.meekdev.moud.core.ui.ViewportFrame;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class Joints {

    private static final Set<Integer> HELD = new HashSet<>();
    private static final double SERVO_GAIN = 8;
    private static final double TICK = 0.05;
    private static final double WINCH_EASE = 0.1;
    private static final double EPSILON = 1.0e-4;

    private record Built(B3Joint joint, int shape) {}

    private record Ends(Part part0, Part part1, CFrame frame0, CFrame frame1) {}

    private record Range(float low, float high) {

        static Range of(double a, double b) {
            return new Range((float) Math.min(a, b), (float) Math.max(a, b));
        }

        static Range degrees(double a, double b) {
            return new Range((float) Math.toRadians(Math.min(a, b)), (float) Math.toRadians(Math.max(a, b)));
        }
    }

    private final Map<Integer, Built> built = new HashMap<>();

    public static boolean holds(int partId) {
        return HELD.contains(partId);
    }

    public void settle(@Nullable InstanceTree tree, SubLevels shapes, @Nullable B3World world) {
        if (tree == null || world == null) {
            clear();
            return;
        }
        Set<Integer> held = new HashSet<>();
        Set<Integer> seen = new HashSet<>();
        for (WeldConstraint weld : tree.ofClass(Classes.WELD_CONSTRAINT)) {
            seen.add(weld.id());
            Ends ends = weld.enabled && weld.part0 instanceof Part a && weld.part1 instanceof Part b
                    ? ends(a, b, Transforms.world(b), Transforms.world(b)) : null;
            settleOne(weld, ends, false, shapes, world, held);
        }
        for (Constraint constraint : tree.ofClass(Classes.CONSTRAINT)) {
            seen.add(constraint.id());
            Ends ends = null;
            if (constraint.enabled
                    && constraint.attachment0 instanceof Attachment a0 && constraint.attachment1 instanceof Attachment a1
                    && a0.parent() instanceof Part p0 && a1.parent() instanceof Part p1) {
                ends = ends(p0, p1, Transforms.world(a0), Transforms.world(a1));
            }
            settleOne(constraint, ends, constraint.collideConnected, shapes, world, held);
        }
        Iterator<Map.Entry<Integer, Built>> stale = built.entrySet().iterator();
        while (stale.hasNext()) {
            Map.Entry<Integer, Built> entry = stale.next();
            if (seen.contains(entry.getKey())) continue;
            destroy(entry.getValue());
            stale.remove();
        }
        Set<Integer> added = new HashSet<>(held);
        added.removeAll(HELD);
        HELD.clear();
        HELD.addAll(held);
        for (int id : added) shapes.refresh(id);
    }

    private static @Nullable Ends ends(Part a, Part b, CFrame frame0, CFrame frame1) {
        if (a == b || !a.isAlive() || !b.isAlive() || ViewportFrame.inside(a) || ViewportFrame.inside(b)) return null;
        return new Ends(a, b, frame0, frame1);
    }

    private void settleOne(Instance instance, @Nullable Ends ends, boolean collide,
                           SubLevels shapes, B3World world, Set<Integer> held) {
        B3Body a = null;
        B3Body b = null;
        if (ends != null) {
            held.add(ends.part0().id());
            held.add(ends.part1().id());
            a = shapes.body(ends.part0().id());
            b = shapes.body(ends.part1().id());
        }
        Built current = built.get(instance.id());
        boolean usable = a != null && b != null && a.isValid() && b.isValid()
                && (a.type() == B3BodyType.DYNAMIC || b.type() == B3BodyType.DYNAMIC);
        if (!usable) {
            if (current != null) destroy(built.remove(instance.id()));
            active(instance, false);
            return;
        }
        int shape = Objects.hash(ends.part0().id(), ends.part1().id(),
                System.identityHashCode(a), System.identityHashCode(b), structure(instance));
        if (current == null || current.shape() != shape || !current.joint().isValid()) {
            if (current != null) destroy(current);
            current = new Built(create(instance, ends, a, b, world), shape);
            current.joint().setCollideConnected(collide);
            built.put(instance.id(), current);
        }
        drive(instance, current.joint(), ends, a, b);
        active(instance, true);
    }

    private static Object structure(Instance instance) {
        return switch (instance) {
            case HingeConstraint hinge -> List.of(hinge.collideConnected);
            case PrismaticConstraint slide -> List.of(slide.collideConnected);
            case BallSocketConstraint ball -> List.of(ball.collideConnected);
            case RopeConstraint rope -> List.of(rope.collideConnected);
            case SpringConstraint spring -> List.of(spring.collideConnected);
            case RodConstraint rod -> List.of(rod.collideConnected);
            default -> List.of();
        };
    }

    private static B3Joint create(Instance instance, Ends ends, B3Body a, B3Body b, B3World world) {
        Vec3 anchor0 = BoxFrames.vec(ends.frame0().position());
        Vec3 anchor1 = BoxFrames.vec(ends.frame1().position());
        Vec3 jointAxis = BoxFrames.vec(ends.frame0().rightVector());
        return switch (instance) {
            case HingeConstraint ignored -> world.createRevoluteJoint(a, b, anchor0, jointAxis);
            case PrismaticConstraint ignored -> world.createPrismaticJoint(a, b, anchor0, jointAxis);
            case BallSocketConstraint ignored -> world.createSphericalJoint(a, b, anchor0);
            case RopeConstraint rope -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) rope.length);
            case SpringConstraint spring -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) spring.freeLength);
            case RodConstraint rod -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) rod.length);
            default -> world.createWeldJoint(a, b, anchor1);
        };
    }

    private static void drive(Instance instance, B3Joint joint, Ends ends, B3Body a, B3Body b) {
        switch (instance) {
            case HingeConstraint hinge when joint instanceof B3RevoluteJoint revolute -> hinge(hinge, revolute);
            case PrismaticConstraint slide when joint instanceof B3PrismaticJoint prismatic -> slide(slide, prismatic);
            case BallSocketConstraint ball when joint instanceof B3SphericalJoint spherical -> ball(ball, spherical);
            case RopeConstraint rope when joint instanceof B3DistanceJoint distance -> rope(rope, distance, ends);
            case RodConstraint rod when joint instanceof B3DistanceJoint distance -> rod(rod, distance, ends);
            case SpringConstraint spring when joint instanceof B3DistanceJoint distance -> spring(spring, distance, a, b);
            default -> { }
        }
    }

    private static void hinge(HingeConstraint hinge, B3RevoluteJoint revolute) {
        revolute.enableLimit(hinge.limitsEnabled);
        if (hinge.limitsEnabled) {
            Range limits = Range.degrees(hinge.lowerAngle, hinge.upperAngle);
            revolute.setLimits(limits.low(), limits.high());
        }
        double angle = Math.toDegrees(revolute.angle());
        switch (hinge.actuatorType) {
            case MOTOR -> motor(revolute, hinge.angularVelocity, hinge.motorMaxTorque);
            case SERVO -> {
                double error = Math.toRadians(Math.IEEEremainder(hinge.targetAngle - angle, 360));
                double speed = Math.clamp(error * SERVO_GAIN, -hinge.angularSpeed, hinge.angularSpeed);
                motor(revolute, speed, hinge.servoMaxTorque);
            }
            case NONE -> revolute.enableMotor(false);
        }
        write(hinge, "currentAngle", angle);
    }

    private static void slide(PrismaticConstraint slide, B3PrismaticJoint prismatic) {
        prismatic.enableLimit(slide.limitsEnabled);
        if (slide.limitsEnabled) {
            Range limits = Range.of(slide.lowerLimit, slide.upperLimit);
            prismatic.setLimits(limits.low(), limits.high());
        }
        double at = prismatic.translation();
        switch (slide.actuatorType) {
            case MOTOR -> slider(prismatic, slide.velocity, slide.motorMaxForce);
            case SERVO -> {
                double speed = Math.clamp((slide.targetPosition - at) * SERVO_GAIN, -slide.speed, slide.speed);
                slider(prismatic, speed, slide.servoMaxForce);
            }
            case NONE -> prismatic.enableMotor(false);
        }
        write(slide, "currentPosition", at);
    }

    private static void ball(BallSocketConstraint ball, B3SphericalJoint spherical) {
        spherical.enableConeLimit(ball.limitsEnabled);
        if (ball.limitsEnabled) spherical.setConeLimit((float) Math.toRadians(ball.upperAngle));
        spherical.enableTwistLimit(ball.twistLimitsEnabled);
        if (ball.twistLimitsEnabled) {
            Range twist = Range.degrees(ball.twistLowerAngle, ball.twistUpperAngle);
            spherical.setTwistLimits(twist.low(), twist.high());
        }
    }

    private static void rope(RopeConstraint rope, B3DistanceJoint distance, Ends ends) {
        if (rope.winchEnabled) winch(rope, distance);
        distance.enableSpring(true);
        distance.setSpring(0, 0);
        distance.enableLimit(true);
        distance.setLengthRange(0, (float) rope.length);
        write(rope, "currentDistance", ends.frame0().position().distance(ends.frame1().position()));
    }

    private static void rod(RodConstraint rod, B3DistanceJoint distance, Ends ends) {
        distance.enableSpring(false);
        distance.enableLimit(false);
        distance.setLength((float) rod.length);
        write(rod, "currentDistance", ends.frame0().position().distance(ends.frame1().position()));
    }

    private static void spring(SpringConstraint spring, B3DistanceJoint distance, B3Body a, B3Body b) {
        double mass = effectiveMass(a, b);
        double omega = Math.sqrt(spring.stiffness / mass);
        double ratio = spring.stiffness > 0 ? spring.damping / (2 * Math.sqrt(spring.stiffness * mass)) : 0;
        distance.setLength((float) spring.freeLength);
        distance.enableSpring(true);
        distance.setSpring((float) (omega / (2 * Math.PI)), (float) ratio);
        distance.enableLimit(spring.limitsEnabled);
        if (spring.limitsEnabled) {
            Range limits = Range.of(spring.minLength, spring.maxLength);
            distance.setLengthRange(limits.low(), limits.high());
        }
        write(spring, "currentLength", distance.currentLength());
    }

    private static void winch(RopeConstraint rope, B3DistanceJoint distance) {
        double gap = rope.winchTarget - rope.length;
        if (Math.abs(gap) <= EPSILON) return;
        double eased = Math.min(rope.winchSpeed, Math.abs(gap) * rope.winchResponsiveness * WINCH_EASE);
        double rate = rope.winchResponsiveness <= 0 ? rope.winchSpeed : eased;
        if (gap < 0) {
            var pull = distance.constraintForce();
            double load = Math.sqrt(pull.x() * pull.x() + pull.y() * pull.y() + pull.z() * pull.z());
            if (load > rope.winchForce) return;
        }
        double next = rope.length + Math.signum(gap) * Math.min(Math.abs(gap), rate * TICK);
        write(rope, "length", Math.max(0, next));
        distance.wakeBodies();
    }

    private static double effectiveMass(B3Body a, B3Body b) {
        double ma = a.type() == B3BodyType.DYNAMIC ? a.mass() : 0;
        double mb = b.type() == B3BodyType.DYNAMIC ? b.mass() : 0;
        if (ma <= 0) return Math.max(mb, EPSILON);
        if (mb <= 0) return Math.max(ma, EPSILON);
        return ma * mb / (ma + mb);
    }

    private static void motor(B3RevoluteJoint joint, double speed, double torque) {
        joint.enableMotor(true);
        joint.setMotorSpeed((float) speed);
        joint.setMaxMotorTorque((float) torque);
        joint.wakeBodies();
    }

    private static void slider(B3PrismaticJoint joint, double speed, double force) {
        joint.enableMotor(true);
        joint.setMotorSpeed((float) speed);
        joint.setMaxMotorForce((float) force);
        joint.wakeBodies();
    }

    private static void write(Instance instance, String name, double value) {
        PropertyDef property = instance.def().property(name);
        if (Math.abs(property.getNum(instance) - value) > EPSILON) Instances.setNum(instance, property, value);
    }

    private static void active(Instance instance, boolean on) {
        PropertyDef property = instance.def().property("active");
        if (property.getBool(instance) != on) Instances.setBool(instance, property, on);
    }

    private static void destroy(Built one) {
        if (one.joint().isValid()) one.joint().destroy();
    }

    public void clear() {
        for (Built one : built.values()) destroy(one);
        built.clear();
        HELD.clear();
    }
}
