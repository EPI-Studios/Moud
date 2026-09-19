package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.box3d.B3DistanceJoint;
import com.meekdev.box3d.B3Joint;
import com.meekdev.box3d.B3PrismaticJoint;
import com.meekdev.box3d.B3RevoluteJoint;
import com.meekdev.box3d.B3SphericalJoint;
import com.meekdev.box3d.B3Transform;
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
import com.meekdev.moud.core.math.Vector3;
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
import java.util.function.IntFunction;
import java.util.function.Predicate;
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
    private final Map<Integer, Bouncy> bouncy = new HashMap<>();
    private final LevelPhysics.PreSubStep before = this::beforeStep;
    private final LevelPhysics.PostSubStep after = this::afterStep;
    private @Nullable LevelPhysics stepping;
    private final boolean publishes;

    public Joints() {
        this(true);
    }

    public Joints(boolean publishes) {
        this.publishes = publishes;
    }

    public static boolean holds(int partId) {
        return HELD.contains(partId);
    }

    public void settle(@Nullable InstanceTree tree, SubLevels shapes, @Nullable B3World world) {
        settle(tree, shapes::body, shapes, world, part -> true);
    }

    public void settle(@Nullable InstanceTree tree, IntFunction<@Nullable B3Body> bodies, @Nullable SubLevels shapes,
                       @Nullable B3World world, Predicate<Part> wanted) {
        if (tree == null || world == null) {
            clear();
            return;
        }
        Set<Integer> held = new HashSet<>();
        Set<Integer> seen = new HashSet<>();
        bouncy.clear();
        for (WeldConstraint weld : tree.ofClass(Classes.WELD_CONSTRAINT)) {
            seen.add(weld.id());
            Ends ends = weld.enabled && weld.part0 instanceof Part a && weld.part1 instanceof Part b
                    ? ends(a, b, Transforms.world(b), Transforms.world(b)) : null;
            settleOne(weld, wanted(ends, wanted), false, bodies, world, held);
        }
        for (Constraint constraint : tree.ofClass(Classes.CONSTRAINT)) {
            seen.add(constraint.id());
            Ends ends = null;
            if (constraint.enabled
                    && constraint.attachment0 instanceof Attachment a0 && constraint.attachment1 instanceof Attachment a1
                    && a0.parent() instanceof Part p0 && a1.parent() instanceof Part p1) {
                ends = ends(p0, p1, Transforms.world(a0), Transforms.world(a1));
            }
            settleOne(constraint, wanted(ends, wanted), constraint.collideConnected, bodies, world, held);
        }
        Iterator<Map.Entry<Integer, Built>> stale = built.entrySet().iterator();
        while (stale.hasNext()) {
            Map.Entry<Integer, Built> entry = stale.next();
            if (seen.contains(entry.getKey())) continue;
            destroy(entry.getValue());
            stale.remove();
        }
        if (!publishes || shapes == null) return;
        Set<Integer> added = new HashSet<>(held);
        added.removeAll(HELD);
        HELD.clear();
        HELD.addAll(held);
        for (int id : added) shapes.refresh(id);
    }

    private static @Nullable Ends wanted(@Nullable Ends ends, Predicate<Part> wanted) {
        return ends != null && wanted.test(ends.part0()) && wanted.test(ends.part1()) ? ends : null;
    }

    private static @Nullable Ends ends(Part a, Part b, CFrame frame0, CFrame frame1) {
        if (a == b || !a.isAlive() || !b.isAlive() || ViewportFrame.inside(a) || ViewportFrame.inside(b)) return null;
        return new Ends(a, b, frame0, frame1);
    }

    private void settleOne(Instance instance, @Nullable Ends ends, boolean collide,
                           IntFunction<@Nullable B3Body> bodies, B3World world, Set<Integer> held) {
        B3Body a = null;
        B3Body b = null;
        if (ends != null) {
            held.add(ends.part0().id());
            held.add(ends.part1().id());
            if (publishes && ends.part0().simulatedRemotely() && ends.part1().simulatedRemotely()) {
                Built current = built.remove(instance.id());
                if (current != null) destroy(current);
                active(instance, true);
                if (instance instanceof RopeConstraint rope && rope.winchEnabled) reel(rope);
                return;
            }
            a = bodies.apply(ends.part0().id());
            b = bodies.apply(ends.part1().id());
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
            case HingeConstraint ignored -> pinned(world.createRevoluteJoint(a, b, anchor0, jointAxis), b, ends);
            case PrismaticConstraint ignored -> pinned(world.createPrismaticJoint(a, b, anchor0, jointAxis), b, ends);
            case BallSocketConstraint ignored -> pinned(world.createSphericalJoint(a, b, anchor0), b, ends);
            case RopeConstraint rope -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) rope.length);
            case SpringConstraint spring -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) spring.freeLength);
            case RodConstraint rod -> world.createDistanceJoint(a, b, anchor0, anchor1, (float) rod.length);
            default -> world.createWeldJoint(a, b, anchor1);
        };
    }

    private static B3Joint pinned(B3Joint joint, B3Body b, Ends ends) {
        Vec3 anchor = b.localPoint(BoxFrames.vec(ends.frame1().position()));
        joint.setLocalFrameB(new B3Transform(anchor, joint.localFrameB().rotation()));
        return joint;
    }

    private void drive(Instance instance, B3Joint joint, Ends ends, B3Body a, B3Body b) {
        switch (instance) {
            case HingeConstraint hinge when joint instanceof B3RevoluteJoint revolute -> hinge(hinge, revolute);
            case PrismaticConstraint slide when joint instanceof B3PrismaticJoint prismatic -> slide(slide, prismatic);
            case BallSocketConstraint ball when joint instanceof B3SphericalJoint spherical -> ball(ball, spherical);
            case RopeConstraint rope when joint instanceof B3DistanceJoint distance -> rope(rope, distance, ends, a, b);
            case RodConstraint rod when joint instanceof B3DistanceJoint distance -> rod(rod, distance, ends);
            case SpringConstraint spring when joint instanceof B3DistanceJoint distance -> spring(spring, distance, a, b);
            default -> { }
        }
    }

    private void hinge(HingeConstraint hinge, B3RevoluteJoint revolute) {
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

    private void slide(PrismaticConstraint slide, B3PrismaticJoint prismatic) {
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

    private void rope(RopeConstraint rope, B3DistanceJoint distance, Ends ends, B3Body a, B3Body b) {
        if (rope.winchEnabled) winch(rope, distance);
        distance.enableSpring(true);
        distance.setSpring(0, 0);
        distance.enableLimit(true);
        distance.setLengthRange(0, (float) rope.length);
        if (rope.restitution > 0) bouncy.put(rope.id(), new Bouncy(distance, a, b, rope.length, rope.restitution));
        write(rope, "currentDistance", ends.frame0().position().distance(ends.frame1().position()));
    }

    private void rod(RodConstraint rod, B3DistanceJoint distance, Ends ends) {
        distance.enableSpring(false);
        distance.enableLimit(false);
        distance.setLength((float) rod.length);
        write(rod, "currentDistance", ends.frame0().position().distance(ends.frame1().position()));
    }

    private void spring(SpringConstraint spring, B3DistanceJoint distance, B3Body a, B3Body b) {
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

    private void beforeStep(float dt) {
        for (Bouncy rope : bouncy.values()) rope.before(dt);
    }

    private void afterStep(float dt) {
        for (Bouncy rope : bouncy.values()) rope.after();
    }

    private static final class Bouncy {

        private static final double SLACK = 0.02;
        private static final double SLOWEST = 0.5;

        private final B3DistanceJoint joint;
        private final B3Body a;
        private final B3Body b;
        private final double length;
        private final double restitution;
        private double arriving;

        Bouncy(B3DistanceJoint joint, B3Body a, B3Body b, double length, double restitution) {
            this.joint = joint;
            this.a = a;
            this.b = b;
            this.length = length;
            this.restitution = restitution;
        }

        void before(float dt) {
            arriving = 0;
            if (!joint.isValid() || !a.isValid() || !b.isValid()) return;
            Vec3 pointA = a.worldPoint(joint.localFrameA().position());
            Vec3 pointB = b.worldPoint(joint.localFrameB().position());
            Vector3 along = BoxFrames.vector(pointB).sub(BoxFrames.vector(pointA));
            double apart = along.length();
            if (apart < EPSILON || apart >= length - SLACK) return;
            double speed = separating(along.mul(1 / apart), pointA, pointB);
            if (speed > SLOWEST && apart + speed * dt >= length - SLACK) arriving = speed;
        }

        void after() {
            if (arriving <= 0 || !joint.isValid() || !a.isValid() || !b.isValid()) return;
            Vec3 pointA = a.worldPoint(joint.localFrameA().position());
            Vec3 pointB = b.worldPoint(joint.localFrameB().position());
            Vector3 along = BoxFrames.vector(pointB).sub(BoxFrames.vector(pointA));
            if (along.lengthSq() < EPSILON) return;
            Vector3 n = along.normalize();
            double change = -restitution * arriving - separating(n, pointA, pointB);
            double inverseA = inverseMass(a);
            double inverseB = inverseMass(b);
            if (change >= 0 || inverseA + inverseB <= 0) return;
            Vector3 impulse = n.mul(change / (inverseA + inverseB));
            if (inverseB > 0) b.applyImpulseAt(BoxFrames.vec(impulse), pointB);
            if (inverseA > 0) a.applyImpulseAt(BoxFrames.vec(impulse.mul(-1)), pointA);
        }

        private double separating(Vector3 n, Vec3 pointA, Vec3 pointB) {
            Vector3 velocityA = BoxFrames.vector(a.velocityAtPoint(pointA));
            Vector3 velocityB = BoxFrames.vector(b.velocityAtPoint(pointB));
            return velocityB.sub(velocityA).dot(n);
        }
    }

    private void winch(RopeConstraint rope, B3DistanceJoint distance) {
        if (rope.winchTarget < rope.length) {
            var pull = distance.constraintForce();
            double load = Math.sqrt(pull.x() * pull.x() + pull.y() * pull.y() + pull.z() * pull.z());
            if (load > rope.winchForce) return;
        }
        if (reel(rope)) distance.wakeBodies();
    }

    private boolean reel(RopeConstraint rope) {
        double gap = rope.winchTarget - rope.length;
        if (Math.abs(gap) <= EPSILON) return false;
        double eased = Math.min(rope.winchSpeed, Math.abs(gap) * rope.winchResponsiveness * WINCH_EASE);
        double rate = rope.winchResponsiveness <= 0 ? rope.winchSpeed : eased;
        double next = rope.length + Math.signum(gap) * Math.min(Math.abs(gap), rate * TICK);
        write(rope, "length", Math.max(0, next));
        return true;
    }

    private static double effectiveMass(B3Body a, B3Body b) {
        double ma = a.type() == B3BodyType.DYNAMIC ? a.mass() : 0;
        double mb = b.type() == B3BodyType.DYNAMIC ? b.mass() : 0;
        if (ma <= 0) return Math.max(mb, EPSILON);
        if (mb <= 0) return Math.max(ma, EPSILON);
        return ma * mb / (ma + mb);
    }

    private static double inverseMass(B3Body body) {
        return body.type() == B3BodyType.DYNAMIC && body.mass() > 0 ? 1 / body.mass() : 0;
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

    private void write(Instance instance, String name, double value) {
        if (!publishes) return;
        PropertyDef property = instance.def().property(name);
        if (Math.abs(property.getNum(instance) - value) > EPSILON) Instances.setNum(instance, property, value);
    }

    private void active(Instance instance, boolean on) {
        if (!publishes) return;
        PropertyDef property = instance.def().property("active");
        if (property.getBool(instance) != on) Instances.setBool(instance, property, on);
    }

    private static void destroy(Built one) {
        if (one.joint().isValid()) one.joint().destroy();
    }

    public int size() {
        return built.size();
    }

    public void attach(@Nullable LevelPhysics physics) {
        if (physics == stepping) return;
        if (stepping != null) {
            stepping.removePreSubStep(before);
            stepping.removePostSubStep(after);
        }
        stepping = physics;
        if (physics == null) return;
        physics.addPreSubStep(before);
        physics.addPostSubStep(after);
    }

    public void clear() {
        bouncy.clear();
        for (Built one : built.values()) destroy(one);
        built.clear();
        if (publishes) HELD.clear();
    }
}
