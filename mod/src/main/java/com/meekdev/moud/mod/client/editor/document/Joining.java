package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.physics.RodConstraint;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.SpringConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Joining {

    private static final double PARALLEL = 0.99;
    private static final double FLAT = 0.999;

    private Joining() {}

    public static CFrame onSurface(CFrame part, SurfacePoint point) {
        Vector3 x = point.normal().normalize();
        Vector3 hint = part.upVector();
        if (Math.abs(hint.dot(x)) > PARALLEL) hint = part.lookVector().neg();
        Vector3 y = hint.sub(x.mul(hint.dot(x))).normalize();
        return new CFrame(point.at(), Quat.fromAxes(x, y, x.cross(y)));
    }

    public static CFrame alongside(CFrame first, Vector3 at) {
        return first.withPosition(at);
    }

    public static CFrame local(CFrame part, CFrame world) {
        return part.inverse().mul(world);
    }

    public static Vector3 snapOnFace(CFrame part, Vector3 half, SurfacePoint point, double step) {
        if (step <= 0) return point.at();
        Vector3 local = part.pointToObject(point.at());
        Vector3 normal = part.vectorToObject(point.normal()).normalize();
        double[] n = {Math.abs(normal.x()), Math.abs(normal.y()), Math.abs(normal.z())};
        int face = n[0] >= n[1] && n[0] >= n[2] ? 0 : n[1] >= n[2] ? 1 : 2;
        if (n[face] < FLAT) return point.at();
        double[] p = {local.x(), local.y(), local.z()};
        double[] h = {half.x(), half.y(), half.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (axis != face) p[axis] = Math.clamp(Math.round(p[axis] / step) * step, -h[axis], h[axis]);
        }
        return part.pointToWorld(new Vector3(p[0], p[1], p[2]));
    }

    public static String freeName(Instance parent, String base, Set<String> taken) {
        String name = base;
        for (int n = 2; parent.child(name) != null || taken.contains(name); n++) name = base + n;
        taken.add(name);
        return name;
    }

    static Attachment attachment(Instance holder, String name, CFrame local) {
        Attachment made = Instances.create(Classes.ATTACHMENT, holder, name);
        made.cframe = local;
        return made;
    }

    static Instance constraint(ConstraintKind kind, Instance holder, String name, double distance) {
        Instance made = Instances.create(kind.def(), holder, name);
        switch (made) {
            case RopeConstraint rope -> rope.length = distance;
            case RodConstraint rod -> rod.length = distance;
            case SpringConstraint spring -> spring.freeLength = distance;
            default -> { }
        }
        return made;
    }

    static List<Edit> weld(InstanceRef part0, InstanceRef part1, String weld, boolean select, String label) {
        Paste paste = new Paste(weld, part0, new ArrayList<>(), new ArrayList<>(), select, label);
        return List.of(paste,
                new Link(paste.roots(), index(ConstraintKind.WELD, "part0"), List.of(part0), true, label),
                new Link(paste.roots(), index(ConstraintKind.WELD, "part1"), List.of(part1), true, label));
    }

    static List<Edit> attached(ConstraintKind kind, InstanceRef part0, InstanceRef part1, String attachment0, String attachment1,
                               String constraint, String label) {
        Paste first = new Paste(attachment0, part0, new ArrayList<>(), new ArrayList<>(), false, label);
        Paste second = new Paste(attachment1, part1, new ArrayList<>(), new ArrayList<>(), false, label);
        Paste joint = new Paste(constraint, part0, new ArrayList<>(), new ArrayList<>(), true, label);
        return List.of(first, second, joint,
                new Link(joint.roots(), index(kind, "attachment0"), first.roots(), true, label),
                new Link(joint.roots(), index(kind, "attachment1"), second.roots(), true, label));
    }

    private static int index(ConstraintKind kind, String property) {
        return kind.def().property(property).index();
    }
}
