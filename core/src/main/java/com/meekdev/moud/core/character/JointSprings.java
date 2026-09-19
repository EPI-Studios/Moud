package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class JointSprings {

    private static final Vector3 DOWN = new Vector3(0, -1, 0);
    private static final int SUBSTEPS = 4;
    private static final double LONGEST_STEP = 0.1;
    private static final double TELEPORT = 4;
    private static final int DEEPEST = 64;

    private static final class State {
        Vector3 tip;
        Vector3 velocity = Vector3.ZERO;

        State(Vector3 tip) {
            this.tip = tip;
        }
    }

    private static final Map<JointSpring, State> STATES = new WeakHashMap<>();

    private JointSprings() {}

    static void forget() {
        STATES.clear();
    }

    public static Instance joint(JointSpring spring) {
        Instance joint = spring.joint != null ? spring.joint : spring.parent();
        return Rigs.posable(joint) && joint.isAlive() ? joint : null;
    }

    public static void step(InstanceTree tree, double dt) {
        step(tree, dt, joint -> true);
    }

    public static void step(InstanceTree tree, double dt, Predicate<Instance> joints) {
        List<JointSpring> springs = new ArrayList<>();
        for (Instance instance : tree.ofClass(Classes.JOINT_SPRING)) {
            if (!(instance instanceof JointSpring spring)) continue;
            Instance joint = joint(spring);
            if (joint != null && !joints.test(joint)) continue;
            if (spring.enabled && spring.weight > 0 && joint(spring) != null) springs.add(spring);
            else STATES.remove(spring);
        }
        springs.sort(Comparator.comparingInt(spring -> depth(joint(spring))));
        for (JointSpring spring : springs) step(spring, joint(spring), dt);
    }

    private static int depth(Instance joint) {
        int depth = 0;
        for (Instance at = Rigs.rigParent(joint); at != null && depth < DEEPEST; at = Rigs.rigParent(at)) depth++;
        return depth;
    }

    public static void step(JointSpring spring, Instance joint, double dt) {
        Posing.touch(joint);
        CFrame frame = Rigs.frame(joint);
        Vector3 pivot = frame.position();
        Vector3 axis = spring.axis.lengthSq() < 1e-12 ? DOWN : spring.axis.normalize();
        Vector3 rest = frame.rotation().rotate(axis).mul(spring.length);
        State state = STATES.get(spring);
        if (state == null || state.tip.distance(pivot.add(rest)) > TELEPORT + spring.length) {
            state = new State(pivot.add(rest));
            STATES.put(spring, state);
        }
        double h = Math.min(dt, LONGEST_STEP) / SUBSTEPS;
        if (h <= 0) return;
        for (int n = 0; n < SUBSTEPS; n++) {
            Vector3 pull = pivot.add(rest).sub(state.tip).mul(spring.stiffness)
                    .sub(state.velocity.mul(spring.damping))
                    .add(spring.gravity);
            Vector3 was = state.tip;
            Vector3 velocity = state.velocity.add(pull.mul(h));
            Vector3 arm = was.add(velocity.mul(h)).sub(pivot);
            if (arm.lengthSq() < 1e-12) arm = rest;
            Vector3 held = pivot.add(limit(rest, arm.normalize().mul(spring.length), Math.toRadians(spring.maxAngle)));
            state.velocity = held.sub(was).mul(1 / h);
            state.tip = held;
        }
        Quat bend = Quat.IDENTITY.slerp(Quat.fromTo(rest, state.tip.sub(pivot)), spring.weight);
        Rigs.turnTo(joint, bend.mul(frame.rotation()));
    }

    static Vector3 limit(Vector3 rest, Vector3 arm, double most) {
        double lengths = rest.length() * arm.length();
        if (lengths < 1e-12) return arm;
        double angle = Math.acos(Math.clamp(rest.dot(arm) / lengths, -1, 1));
        if (angle <= most) return arm;
        Vector3 axis = rest.cross(arm);
        if (axis.lengthSq() < 1e-12) return rest.normalize().mul(arm.length());
        return Quat.axisAngle(axis.normalize(), most).rotate(rest.normalize()).mul(arm.length());
    }
}
