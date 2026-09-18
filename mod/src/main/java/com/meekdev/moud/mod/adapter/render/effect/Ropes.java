package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.HingeConstraint;
import com.meekdev.moud.core.physics.PrismaticConstraint;
import com.meekdev.moud.core.physics.RodConstraint;
import com.meekdev.moud.core.physics.RopeCurve;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.SpringConstraint;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.Meshes;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.List;

final class Ropes {

    private static final double JOINT_THICKNESS = 0.05;
    private static final double AXIS_LENGTH = 0.6;
    private static final int ROPE_SEGMENTS = 24;
    private static final int COIL_SEGMENTS = 16;
    private static final int SIDES = 6;
    private static final double SHADE_FLOOR = 0.55;
    private static final Vector3 SUN = new Vector3(0.3, 1, 0.2).normalize();
    private static final double NEAR_VERTICAL = 0.9;
    private static final double MIN_COIL_SQ = 1.0e-8;
    private static final double MIN_SEGMENT_SQ = 1.0e-10;
    private static final double MIN_NORMAL_SQ = 1.0e-10;
    private static final double MIN_STEP_SQ = 1.0e-12;
    private static final double MIN_SIDE_SQ = 1.0e-12;

    private Ropes() {}

    static void draw(QuadBatch batch, Effects.View view, InstanceTree tree, float partialTick) {
        boolean started = false;
        for (Instance instance : tree.ofClass(Classes.CONSTRAINT)) {
            if (!(instance instanceof Constraint constraint) || !constraint.visible || !constraint.enabled) continue;
            if (!(constraint.attachment0 instanceof Attachment a0) || !a0.isAlive() || ViewportFrame.inside(a0)) continue;
            if (!started) {
                batch.use(QuadBatch.Layer.WORLD, EffectTextures.white());
                started = true;
            }
            CFrame at0 = ClientScene.motion().sample(a0, partialTick);
            batch.tint(constraint.color, 1, 1);
            Color colour = constraint.color;
            if (constraint instanceof HingeConstraint || constraint instanceof PrismaticConstraint) {
                Vector3 axis = at0.rightVector().mul(AXIS_LENGTH);
                batch.light(Effects.light(at0.position()), 1, 0);
                segment(batch, view, at0.position().sub(axis), at0.position().add(axis), JOINT_THICKNESS);
            }
            if (!(constraint.attachment1 instanceof Attachment a1) || !a1.isAlive()) continue;
            Vector3 from = at0.position();
            Vector3 to = ClientScene.motion().sample(a1, partialTick).position();
            batch.light(Effects.light(from.lerp(to, 0.5)), 1, 0);
            switch (constraint) {
                case RopeConstraint rope -> {
                    if (!Meshes.ready(rope.mesh)) rope(batch, colour, from, to, rope.length, rope.thickness);
                }
                case SpringConstraint spring -> coil(batch, colour, from, to, spring.coils, spring.radius, spring.thickness);
                case RodConstraint rod -> tube(batch, colour, List.of(from, to), rod.thickness);
                default -> segment(batch, view, from, to, JOINT_THICKNESS);
            }
        }
    }

    private static void rope(QuadBatch batch, Color colour, Vector3 from, Vector3 to, double length, double thickness) {
        List<Vector3> points = new ArrayList<>(ROPE_SEGMENTS + 1);
        for (int n = 0; n <= ROPE_SEGMENTS; n++) points.add(RopeCurve.at(from, to, length, (double) n / ROPE_SEGMENTS));
        tube(batch, colour, points, thickness);
    }

    private static void coil(QuadBatch batch, Color colour, Vector3 from, Vector3 to,
                             double coils, double radius, double thickness) {
        Vector3 axis = to.sub(from);
        if (axis.lengthSq() < MIN_COIL_SQ || coils <= 0 || radius <= 0) {
            tube(batch, colour, List.of(from, to), thickness);
            return;
        }
        Vector3 along = axis.normalize();
        Vector3 u = along.cross(notAlong(along)).normalize();
        Vector3 v = along.cross(u);
        int steps = Math.max(4, (int) Math.ceil(coils * COIL_SEGMENTS));
        List<Vector3> points = new ArrayList<>(steps + 3);
        points.add(from);
        for (int n = 0; n <= steps; n++) {
            double t = (double) n / steps;
            double turn = 2 * Math.PI * coils * t;
            Vector3 centre = from.add(axis.mul(t));
            Vector3 across = u.mul(Math.cos(turn) * radius);
            Vector3 up = v.mul(Math.sin(turn) * radius);
            points.add(centre.add(across).add(up));
        }
        points.add(to);
        tube(batch, colour, points, thickness);
    }

    private static void tube(QuadBatch batch, Color colour, List<Vector3> points, double thickness) {
        double r = thickness * 0.5;
        Vector3[] previous = null;
        Vector3 previousAt = null;
        Vector3 reference = null;
        for (int n = 0; n < points.size(); n++) {
            Vector3 here = points.get(n);
            Vector3 next = points.get(Math.min(n + 1, points.size() - 1));
            Vector3 dir = next.sub(points.get(Math.max(n - 1, 0)));
            if (dir.lengthSq() < MIN_STEP_SQ) continue;
            dir = dir.normalize();
            Vector3 u = reference == null ? Vector3.ZERO : reference.sub(dir.mul(reference.dot(dir)));
            if (u.lengthSq() < MIN_NORMAL_SQ) u = dir.cross(notAlong(dir));
            u = u.normalize();
            Vector3 v = dir.cross(u);
            reference = u;
            Vector3[] ring = new Vector3[SIDES];
            for (int k = 0; k < SIDES; k++) {
                double a = 2 * Math.PI * k / SIDES;
                ring[k] = u.mul(Math.cos(a)).add(v.mul(Math.sin(a)));
            }
            if (previous != null && here.sub(previousAt).lengthSq() > MIN_STEP_SQ) {
                Vector3 last = previousAt;
                for (int k = 0; k < SIDES; k++) {
                    int j = (k + 1) % SIDES;
                    Vector3 normal = ring[k].add(ring[j]).normalize();
                    double shade = SHADE_FLOOR + (1 - SHADE_FLOOR) * Math.max(0, normal.dot(SUN));
                    batch.tint(colour, 1, shade);
                    batch.corner(last.add(previous[k].mul(r)), 0, 0);
                    batch.corner(last.add(previous[j].mul(r)), 1, 0);
                    batch.corner(here.add(ring[j].mul(r)), 1, 1);
                    batch.corner(here.add(ring[k].mul(r)), 0, 1);
                }
            }
            previous = ring;
            previousAt = here;
        }
        batch.tint(colour, 1, 1);
    }

    private static void segment(QuadBatch batch, Effects.View view, Vector3 from, Vector3 to, double thickness) {
        Vector3 along = to.sub(from);
        if (along.lengthSq() < MIN_SEGMENT_SQ) return;
        Vector3 facing = view.eye().sub(from.add(to).mul(0.5));
        Vector3 side = along.cross(facing);
        if (side.lengthSq() < MIN_SIDE_SQ) side = along.cross(view.up());
        if (side.lengthSq() < MIN_SIDE_SQ) return;
        side = side.normalize().mul(thickness * 0.5);
        batch.corner(from.sub(side), 0, 0);
        batch.corner(from.add(side), 1, 0);
        batch.corner(to.add(side), 1, 1);
        batch.corner(to.sub(side), 0, 1);
    }

    private static Vector3 notAlong(Vector3 direction) {
        return Math.abs(direction.y()) < NEAR_VERTICAL ? Vector3.UP : Vector3.RIGHT;
    }
}
