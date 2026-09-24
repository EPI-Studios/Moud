package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.box3d.B3Body;
import com.meekdev.box3d.B3BodyType;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.part.PartShape;
import com.meekdev.moud.core.part.Shapes;
import com.meekdev.moud.core.physics.ActuatorRelativeTo;
import com.meekdev.moud.core.physics.AlignOrientation;
import com.meekdev.moud.core.physics.AlignPosition;
import com.meekdev.moud.core.physics.AlignmentMode;
import com.meekdev.moud.core.physics.AngularVelocity;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.Inertia;
import com.meekdev.moud.core.physics.LinearVelocity;
import com.meekdev.moud.core.physics.Mover;
import com.meekdev.moud.core.physics.Movers;
import com.meekdev.moud.core.physics.Torque;
import com.meekdev.moud.core.physics.VectorForce;
import com.meekdev.moud.core.ui.ViewportFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class MoverBodies {

    private static final double TINY = 1.0e-9;
    private static final double NO_PUSH_SQ = 1.0e-18;

    private record Member(Part part, B3Body body) {}

    private record End(B3Body body, CFrame offset) {

        CFrame frame() {
            return BoxFrames.of(body).mul(offset);
        }
    }

    private record Load(double mass, Inertia inertia) {}

    private record Driven(Constraint mover, End first, @Nullable End second, @Nullable CFrame fixed,
                          List<Member> group, List<Member> others) {

        boolean alive() {
            return first.body().isValid() && (second == null || second.body().isValid());
        }

        B3Body body() {
            return first.body();
        }

        @Nullable B3Body other() {
            return second == null ? null : second.body();
        }

        @Nullable CFrame goalFrame() {
            return second == null ? fixed : second.frame();
        }

        boolean pushing() {
            B3Body other = other();
            return Movers.pushesSecond(mover) && other != null && other.type() == B3BodyType.DYNAMIC;
        }

        Vector3 fall(Vector3 gravity) {
            return pushing() ? Vector3.ZERO : gravity.mul(body().gravityScale());
        }

        double mass() {
            double mass = load(group).mass();
            return pushing() ? reducedMass(mass, load(others).mass()) : mass;
        }

        Inertia inertia() {
            Inertia inertia = load(group).inertia();
            return pushing() ? Inertia.between(inertia, load(others).inertia()) : inertia;
        }
    }

    private final List<Driven> driven = new ArrayList<>();
    private final LevelPhysics.PreSubStep before = this::beforeStep;
    private final boolean publishes;
    private @Nullable LevelPhysics stepping;

    public MoverBodies(boolean publishes) {
        this.publishes = publishes;
    }

    public void settle(@Nullable InstanceTree tree, IntFunction<@Nullable B3Body> bodies, Predicate<Part> wanted) {
        driven.clear();
        if (tree == null) return;
        Map<Part, List<Part>> groups = null;
        for (Constraint constraint : tree.ofClass(Classes.CONSTRAINT)) {
            if (!(constraint instanceof Mover)) continue;
            Part part = owner(constraint);
            if (part == null) {
                active(constraint, false);
                continue;
            }
            if (!wanted.test(part)) continue;
            if (publishes && part.simulatedRemotely()) {
                active(constraint, true);
                continue;
            }

            B3Body body = bodies.apply(part.id());
            Attachment second = constraint.attachment1 instanceof Attachment a1 && a1.isAlive() ? a1 : null;
            if (!dynamic(body) || Movers.needsSecond(constraint) && second == null) {
                active(constraint, false);
                continue;
            }
            if (groups == null) groups = Assemblies.join(tree);
            driven.add(drive(constraint, part, body, second, groups, bodies));
            active(constraint, true);
        }
    }

    private static @Nullable Part owner(Constraint constraint) {
        if (!constraint.enabled || !(constraint.attachment0 instanceof Attachment first) || !first.isAlive()) return null;
        if (!(first.parent() instanceof Part part) || !part.isAlive() || ViewportFrame.inside(part)) return null;
        return part;
    }

    private static Driven drive(Constraint mover, Part part, B3Body body, @Nullable Attachment second,
                                Map<Part, List<Part>> groups, IntFunction<@Nullable B3Body> bodies) {
        End first = new End(body, offset(part, (Attachment) mover.attachment0));
        List<Member> group = members(groups, part, bodies);
        if (second == null) return new Driven(mover, first, null, null, group, List.of());

        B3Body other = second.parent() instanceof Part holder ? bodies.apply(holder.id()) : null;
        if (!usable(other)) return new Driven(mover, first, null, Transforms.world(second), group, List.of());
        Part holder = (Part) second.parent();
        End end = new End(other, offset(holder, second));
        return new Driven(mover, first, end, null, group, members(groups, holder, bodies));
    }

    private static boolean usable(@Nullable B3Body body) {
        return body != null && body.isValid();
    }

    private static boolean dynamic(@Nullable B3Body body) {
        return usable(body) && body.type() == B3BodyType.DYNAMIC;
    }

    private static CFrame offset(Part part, Attachment attachment) {
        return Transforms.world(part).inverse().mul(Transforms.world(attachment));
    }

    private static List<Member> members(Map<Part, List<Part>> groups, Part part, IntFunction<@Nullable B3Body> bodies) {
        List<Member> members = new ArrayList<>();
        for (Part joined : groups.getOrDefault(part, List.of(part))) {
            B3Body body = bodies.apply(joined.id());
            if (dynamic(body)) members.add(new Member(joined, body));
        }
        return members;
    }

    private void beforeStep(float dt) {
        if (stepping != null) step(BoxFrames.vector(stepping.world().gravity()), dt);
    }

    void step(Vector3 gravity, double dt) {
        for (Driven one : driven) {
            if (!one.alive()) continue;
            switch (one.mover()) {
                case AlignPosition align -> alignPosition(one, align, gravity, dt);
                case AlignOrientation align -> alignOrientation(one, align, dt);
                case LinearVelocity velocity -> linearVelocity(one, velocity, gravity, dt);
                case AngularVelocity velocity -> angularVelocity(one, velocity, dt);
                case VectorForce force -> vectorForce(one, force);
                case Torque torque -> torque(one, torque);
                default -> { }
            }
        }
    }

    private static void alignPosition(Driven one, AlignPosition align, Vector3 gravity, double dt) {
        CFrame at0 = one.first().frame();
        CFrame at1 = one.goalFrame();
        Vector3 goal = Movers.goal(align, at1);
        if (goal == null) return;

        B3Body body = one.body();
        B3Body other = one.other();
        Vector3 velocity = pointVelocity(body, at0.position());
        if (align.mode == AlignmentMode.TWO_ATTACHMENT && other != null) {
            velocity = velocity.sub(pointVelocity(other, at1.position()));
        }

        Vector3 force = Movers.pull(align, goal.sub(at0.position()), velocity, one.fall(gravity), one.mass(), dt);
        Vector3 at = align.applyAtCenterOfMass ? BoxFrames.vector(body.worldCenterOfMass()) : at0.position();
        body.applyForce(BoxFrames.vec(force), BoxFrames.vec(at));
        if (one.pushing()) other.applyForce(BoxFrames.vec(force.neg()), BoxFrames.vec(at1.position()));
    }

    private static void alignOrientation(Driven one, AlignOrientation align, double dt) {
        CFrame at0 = one.first().frame();
        Quat goal = Movers.goal(align, one.goalFrame());
        if (goal == null) return;

        B3Body body = one.body();
        B3Body other = one.other();
        Vector3 spin = BoxFrames.vector(body.angularVelocity());
        if (align.mode == AlignmentMode.TWO_ATTACHMENT && other != null) {
            spin = spin.sub(BoxFrames.vector(other.angularVelocity()));
        }

        Vector3 torque = Movers.turn(align, at0.rotation(), goal, spin, one.inertia(), dt);
        body.applyTorque(BoxFrames.vec(torque));
        if (one.pushing()) other.applyTorque(BoxFrames.vec(torque.neg()));
    }

    private static void linearVelocity(Driven one, LinearVelocity velocity, Vector3 gravity, double dt) {
        Quat frame = Movers.frame(velocity.relativeTo, one.first().frame(), one.goalFrame());
        if (frame == null) return;

        B3Body body = one.body();
        B3Body other = one.other();
        boolean carried = velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1 && other != null;
        Vector3 base = carried ? pointVelocity(other, BoxFrames.vector(body.worldCenterOfMass())) : Vector3.ZERO;
        Vector3 now = BoxFrames.vector(body.linearVelocity());

        Vector3 force = Movers.hold(velocity, frame, base, now, one.fall(gravity), one.mass(), dt);
        body.applyForceToCenter(BoxFrames.vec(force));
        if (one.pushing()) other.applyForceToCenter(BoxFrames.vec(force.neg()));
    }

    private static void angularVelocity(Driven one, AngularVelocity velocity, double dt) {
        Quat frame = Movers.frame(velocity.relativeTo, one.first().frame(), one.goalFrame());
        if (frame == null) return;

        B3Body body = one.body();
        B3Body other = one.other();
        boolean carried = velocity.relativeTo == ActuatorRelativeTo.ATTACHMENT1 && other != null;
        Vector3 base = carried ? BoxFrames.vector(other.angularVelocity()) : Vector3.ZERO;
        Vector3 now = BoxFrames.vector(body.angularVelocity());

        Vector3 torque = Movers.spin(velocity, frame, base, now, one.inertia(), dt);
        body.applyTorque(BoxFrames.vec(torque));
        if (one.pushing()) other.applyTorque(BoxFrames.vec(torque.neg()));
    }

    private static void vectorForce(Driven one, VectorForce force) {
        CFrame at0 = one.first().frame();
        Quat frame = Movers.frame(force.relativeTo, at0, one.goalFrame());
        if (frame == null || force.force.lengthSq() < NO_PUSH_SQ) return;

        B3Body body = one.body();
        Vector3 push = frame.rotate(force.force);
        if (force.applyAtCenterOfMass) body.applyForceToCenter(BoxFrames.vec(push));
        else body.applyForce(BoxFrames.vec(push), BoxFrames.vec(at0.position()));
    }

    private static void torque(Driven one, Torque torque) {
        Quat frame = Movers.frame(torque.relativeTo, one.first().frame(), one.goalFrame());
        if (frame == null || torque.torque.lengthSq() < NO_PUSH_SQ) return;
        one.body().applyTorque(BoxFrames.vec(frame.rotate(torque.torque)));
    }

    private static Vector3 pointVelocity(B3Body body, Vector3 at) {
        return BoxFrames.vector(body.velocityAtPoint(BoxFrames.vec(at)));
    }

    private static double reducedMass(double first, double second) {
        return first + second <= TINY ? 0 : first * second / (first + second);
    }

    private static Load load(List<Member> members) {
        double mass = 0;
        Vector3 weighted = Vector3.ZERO;
        for (Member member : members) {
            B3Body body = member.body();
            if (!body.isValid()) continue;
            double own = body.mass();
            mass += own;
            weighted = weighted.add(BoxFrames.vector(body.worldCenterOfMass()).mul(own));
        }
        if (mass <= TINY) return new Load(0, Inertia.ZERO);

        Vector3 centre = weighted.mul(1 / mass);
        Inertia inertia = Inertia.ZERO;
        for (Member member : members) {
            B3Body body = member.body();
            if (!body.isValid()) continue;
            double own = body.mass();
            Vector3 offset = BoxFrames.vector(body.worldCenterOfMass()).sub(centre);
            inertia = inertia.plus(ownInertia(member, own).shifted(own, offset));
        }
        return new Load(mass, inertia);
    }

    private static Inertia ownInertia(Member member, double mass) {
        Part part = member.part();
        if (part.shape == PartShape.BALL) return Inertia.ball(mass, Shapes.across(PartShape.BALL, part.size));
        return Inertia.box(mass, part.size, BoxFrames.of(member.body()).rotation());
    }

    private void active(Constraint constraint, boolean on) {
        if (!publishes) return;
        PropertyDef property = constraint.def().property("active");
        if (property.getBool(constraint) != on) Instances.setBool(constraint, property, on);
    }

    public int size() {
        return driven.size();
    }

    public void attach(@Nullable LevelPhysics physics) {
        if (physics == stepping) return;
        if (stepping != null) stepping.removePreSubStep(before);
        stepping = physics;
        if (physics != null) physics.addPreSubStep(before);
    }

    public void clear() {
        driven.clear();
    }
}
