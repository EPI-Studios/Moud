package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class IKControls {

    private static final Vector3 FORWARD = new Vector3(0, 0, -1);
    private static final int CCD_ROUNDS = 12;
    private static final int LONGEST = 32;
    private static final double REACHED = 1e-3;

    private static final Map<IKControl, CFrame> SMOOTHED = new WeakHashMap<>();

    private IKControls() {}

    static void forget() {
        SMOOTHED.clear();
    }

    public static void solve(InstanceTree tree, double dt) {
        solve(tree, dt, joint -> true);
    }

    public static void solve(InstanceTree tree, double dt, Predicate<Instance> joints) {
        List<IKControl> controls = new ArrayList<>();
        for (Instance instance : tree.ofClass(Classes.IK_CONTROL)) {
            if (!(instance instanceof IKControl control)) continue;
            if (control.endEffector != null && !joints.test(control.endEffector)) continue;
            if (control.enabled && control.weight > 0 && Rigs.posable(control.endEffector) && control.endEffector.isAlive()
                    && !ViewModels.inside(control.endEffector)) {
                controls.add(control);
            } else {
                SMOOTHED.remove(control);
            }
        }
        controls.sort(Comparator.comparingDouble(control -> control.priority));
        for (IKControl control : controls) solve(control, dt);
    }

    public static void solve(IKControl control, double dt) {
        solve(control, dt, true);
    }

    static void solve(IKControl control, double dt, boolean touch) {
        CFrame goal = goal(control);
        if (goal == null) return;
        goal = smooth(control, goal, dt);
        Instance end = control.endEffector;
        Instance root = Rigs.posable(control.chainRoot) && control.chainRoot.isAlive() ? control.chainRoot : end;
        List<Instance> chain = chain(root, end);

        List<Instance> moving = switch (control.type) {
            case AIM -> List.of(end);
            case LOOK_AT -> root == end ? List.of(end) : List.of(root, end);
            case POSITION -> chain.size() == 1 ? chain : chain.subList(0, chain.size() - 1);
            case TRANSFORM -> chain;
        };
        Map<Instance, CFrame> before = new LinkedHashMap<>();
        for (Instance joint : moving) {
            if (touch) Posing.touch(joint);
            before.put(joint, Rigs.transform(joint));
        }

        Vector3 target = goal.position();
        switch (control.type) {
            case AIM -> aim(end, control.axis, target);
            case LOOK_AT -> look(root, end, control.rootWeight, target);
            case POSITION -> reach(chain, target, control);
            case TRANSFORM -> {
                reach(chain, target, control);
                Rigs.turnTo(end, goal.rotation());
            }
        }

        if (control.weight >= 1) return;
        for (Map.Entry<Instance, CFrame> entry : before.entrySet()) {
            Rigs.transform(entry.getKey(), entry.getValue().lerp(Rigs.transform(entry.getKey()), control.weight));
        }
    }

    private static CFrame goal(IKControl control) {
        CFrame at = control.target == null || !control.target.isAlive() ? control.targetCframe : Rigs.world(control.target);
        if (at == null) return null;
        return control.offset.equals(CFrame.IDENTITY) ? at : at.mul(control.offset);
    }

    private static CFrame smooth(IKControl control, CFrame goal, double dt) {
        CFrame last = SMOOTHED.get(control);
        CFrame next = last == null || control.smoothTime <= 0 ? goal : last.lerp(goal, 1 - Math.exp(-dt / control.smoothTime));
        SMOOTHED.put(control, next);
        return next;
    }

    static List<Instance> chain(Instance root, Instance end) {
        List<Instance> upward = new ArrayList<>();
        for (Instance at = end; at != null && upward.size() < LONGEST; at = Rigs.rigParent(at)) {
            upward.add(at);
            if (at == root) return new ArrayList<>(upward.reversed());
        }
        return List.of(end);
    }

    static void aim(Instance joint, Vector3 localAxis, Vector3 target) {
        CFrame frame = Rigs.frame(joint);
        Vector3 want = target.sub(frame.position());
        if (want.lengthSq() < 1e-12 || localAxis.lengthSq() < 1e-12) return;
        Vector3 now = frame.rotation().rotate(localAxis);
        Rigs.turnTo(joint, Quat.fromTo(now, want).mul(frame.rotation()));
    }

    static void look(Instance root, Instance end, double rootWeight, Vector3 target) {
        if (root != end && rootWeight > 0) {
            CFrame head = Rigs.frame(end);
            Quat whole = Quat.fromTo(head.rotation().rotate(FORWARD), target.sub(head.position()));
            Quat share = Quat.IDENTITY.slerp(whole, rootWeight);
            Rigs.turnTo(root, share.mul(Rigs.frame(root).rotation()));
        }
        aim(end, FORWARD, target);
    }

    static void reach(List<Instance> chain, Vector3 target, IKControl control) {
        if (chain.size() == 1) {
            aim(chain.getFirst(), control.axis, target);
            return;
        }
        if (chain.size() == 2) {
            Instance root = chain.getFirst();
            CFrame frame = Rigs.frame(root);
            Vector3 toEnd = Rigs.frame(chain.get(1)).position().sub(frame.position());
            aim(root, frame.rotation().inverse().rotate(toEnd), target);
            return;
        }
        if (chain.size() == 3) {
            Vector3 pole = control.pole == null || !control.pole.isAlive() ? null : worldPosition(control.pole);
            twoBone(chain.get(0), chain.get(1), chain.get(2), target, pole);
            return;
        }
        Instance end = chain.getLast();
        for (int round = 0; round < CCD_ROUNDS; round++) {
            for (int n = chain.size() - 2; n >= 0; n--) {
                Instance joint = chain.get(n);
                CFrame frame = Rigs.frame(joint);
                Vector3 tip = Rigs.frame(end).position().sub(frame.position());
                Vector3 want = target.sub(frame.position());
                if (tip.lengthSq() < 1e-12 || want.lengthSq() < 1e-12) continue;
                Rigs.turnTo(joint, Quat.fromTo(tip, want).mul(frame.rotation()));
            }
            if (Rigs.frame(end).position().distance(target) < REACHED) return;
        }
    }

    private static Vector3 worldPosition(Instance instance) {
        CFrame at = Rigs.world(instance);
        return at == null ? null : at.position();
    }

    static void twoBone(Instance upper, Instance middle, Instance end, Vector3 t, Vector3 pole) {
        CFrame upperFrame = Rigs.frame(upper);
        CFrame middleFrame = Rigs.frame(middle);
        Vector3 a = upperFrame.position();
        Vector3 b = middleFrame.position();
        Vector3 c = Rigs.frame(end).position();
        double lab = b.distance(a);
        double lcb = c.distance(b);
        if (lab < 1e-9 || lcb < 1e-9) return;
        double lat = Math.clamp(t.distance(a), 1e-4, lab + lcb - 1e-4);

        double acab0 = angle(c.sub(a), b.sub(a));
        double babc0 = angle(a.sub(b), c.sub(b));
        double acat0 = angle(c.sub(a), t.sub(a));
        double acab1 = Math.acos(Math.clamp((lcb * lcb - lab * lab - lat * lat) / (-2 * lab * lat), -1, 1));
        double babc1 = Math.acos(Math.clamp((lat * lat - lab * lab - lcb * lcb) / (-2 * lab * lcb), -1, 1));

        Vector3 bend = c.sub(a).cross(b.sub(a));
        if (bend.lengthSq() < 1e-12 && pole != null) bend = c.sub(a).cross(pole.sub(a));
        if (bend.lengthSq() < 1e-12) bend = upperFrame.rightVector();
        bend = bend.normalize();

        Quat r0 = Quat.axisAngle(bend, acab1 - acab0);
        Quat r1 = Quat.axisAngle(bend, babc1 - babc0);
        Vector3 swing = c.sub(a).cross(t.sub(a));
        Quat r2 = swing.lengthSq() < 1e-12 ? Quat.IDENTITY : Quat.axisAngle(swing.normalize(), acat0);

        Quat upperWorld = r2.mul(r0).mul(upperFrame.rotation());
        Quat middleWorld = r2.mul(r1).mul(r0).mul(middleFrame.rotation());

        if (pole != null) {
            Vector3 along = t.sub(a);
            if (along.lengthSq() > 1e-12) {
                Vector3 n = along.normalize();
                Vector3 elbow = r2.mul(r0).rotate(b.sub(a));
                Vector3 from = elbow.sub(n.mul(elbow.dot(n)));
                Vector3 to = pole.sub(a).sub(n.mul(pole.sub(a).dot(n)));
                if (from.lengthSq() > 1e-12 && to.lengthSq() > 1e-12) {
                    double turn = Math.atan2(from.cross(to).dot(n), from.dot(to));
                    Quat twist = Quat.axisAngle(n, turn);
                    upperWorld = twist.mul(upperWorld);
                    middleWorld = twist.mul(middleWorld);
                }
            }
        }

        Rigs.turnTo(upper, upperWorld.normalize());
        Rigs.turnTo(middle, middleWorld.normalize());
    }

    private static double angle(Vector3 u, Vector3 v) {
        double lengths = u.length() * v.length();
        return lengths < 1e-12 ? 0 : Math.acos(Math.clamp(u.dot(v) / lengths, -1, 1));
    }
}
