package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.Adornees;
import com.meekdev.moud.core.effect.SelectionBox;
import com.meekdev.moud.core.effect.SelectionSphere;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.ViewportFrame;

final class Selections {

    private static final double SURFACE_GAP = 0.01;
    private static final int RINGS = 24;
    private static final int SLICES = 24;
    private static final int STACKS = 12;
    private static final Vector3[] AXES = {Vector3.RIGHT, Vector3.UP, new Vector3(0, 0, 1)};

    private Selections() {}

    static void draw(QuadBatch batch, InstanceTree tree, float partialTick) {
        for (SelectionBox box : tree.ofClass(Classes.SELECTION_BOX)) {
            if (!box.visible || ViewportFrame.inside(box)) continue;
            Adornees.Box around = Adornees.box(Adornees.target(box, box.adornee), instance -> Effects.world(instance, partialTick));
            if (around != null) box(batch, box, around);
        }
        for (SelectionSphere sphere : tree.ofClass(Classes.SELECTION_SPHERE)) {
            if (!sphere.visible || ViewportFrame.inside(sphere)) continue;
            Adornees.Box around = Adornees.box(Adornees.target(sphere, sphere.adornee), instance -> Effects.world(instance, partialTick));
            if (around != null) sphere(batch, sphere, around);
        }
    }

    private static void box(QuadBatch batch, SelectionBox box, Adornees.Box around) {
        batch.use(QuadBatch.Layer.ON_TOP, EffectTextures.white());
        batch.light(0, 0, 0);
        double thickness = box.lineThickness;
        Vector3 half = around.size().mul(0.5).add(new Vector3(thickness, thickness, thickness).mul(0.5));
        if (box.surfaceTransparency < 1) {
            batch.tint(box.surfaceColor, 1 - box.surfaceTransparency, 1);
            cuboid(batch, around.frame(), Vector3.ZERO, around.size().mul(0.5).add(new Vector3(SURFACE_GAP, SURFACE_GAP, SURFACE_GAP)));
        }
        if (box.transparency >= 1 || thickness <= 0) return;
        batch.tint(box.color, 1 - box.transparency, 1);
        double t = thickness * 0.5;
        for (int axis = 0; axis < 3; axis++) {
            for (int corner = 0; corner < 4; corner++) {
                double a = (corner & 1) == 0 ? -1 : 1;
                double b = (corner & 2) == 0 ? -1 : 1;
                Vector3 centre;
                Vector3 extent;
                switch (axis) {
                    case 0 -> {
                        centre = new Vector3(0, a * half.y(), b * half.z());
                        extent = new Vector3(half.x() + t, t, t);
                    }
                    case 1 -> {
                        centre = new Vector3(a * half.x(), 0, b * half.z());
                        extent = new Vector3(t, half.y() + t, t);
                    }
                    default -> {
                        centre = new Vector3(a * half.x(), b * half.y(), 0);
                        extent = new Vector3(t, t, half.z() + t);
                    }
                }
                cuboid(batch, around.frame(), centre, extent);
            }
        }
    }

    private static void cuboid(QuadBatch batch, CFrame frame, Vector3 centre, Vector3 extent) {
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vector3 out = AXES[axis].mul(sign);
                Vector3 u = AXES[(axis + 1) % 3];
                Vector3 v = AXES[(axis + 2) % 3];
                Vector3 middle = centre.add(out.mul(extent));
                Vector3 across = u.mul(extent);
                Vector3 along = v.mul(extent);
                batch.corner(frame.pointToWorld(middle.sub(across).sub(along)), 0, 0);
                batch.corner(frame.pointToWorld(middle.add(across).sub(along)), 1, 0);
                batch.corner(frame.pointToWorld(middle.add(across).add(along)), 1, 1);
                batch.corner(frame.pointToWorld(middle.sub(across).add(along)), 0, 1);
            }
        }
    }

    private static void sphere(QuadBatch batch, SelectionSphere sphere, Adornees.Box around) {
        batch.use(QuadBatch.Layer.ON_TOP, EffectTextures.white());
        batch.light(0, 0, 0);
        double radius = around.size().length() * 0.5;
        Vector3 centre = around.frame().position();
        if (sphere.surfaceTransparency < 1) {
            batch.tint(sphere.surfaceColor, 1 - sphere.surfaceTransparency, 1);
            for (int stack = 0; stack < STACKS; stack++) {
                for (int slice = 0; slice < SLICES; slice++) {
                    batch.corner(onSphere(centre, radius, stack, slice), 0, 0);
                    batch.corner(onSphere(centre, radius, stack, slice + 1), 1, 0);
                    batch.corner(onSphere(centre, radius, stack + 1, slice + 1), 1, 1);
                    batch.corner(onSphere(centre, radius, stack + 1, slice), 0, 1);
                }
            }
        }
        if (sphere.transparency >= 1) return;
        batch.tint(sphere.color, 1 - sphere.transparency, 1);
        double band = Math.max(0.02, radius * 0.02);
        for (int axis = 0; axis < 3; axis++) {
            Vector3 normal = AXES[axis];
            Vector3 u = AXES[(axis + 1) % 3];
            Vector3 v = AXES[(axis + 2) % 3];
            for (int n = 0; n < RINGS; n++) {
                double a0 = n * Math.PI * 2 / RINGS;
                double a1 = (n + 1) * Math.PI * 2 / RINGS;
                Vector3 r0 = u.mul(Math.cos(a0)).add(v.mul(Math.sin(a0)));
                Vector3 r1 = u.mul(Math.cos(a1)).add(v.mul(Math.sin(a1)));
                batch.corner(centre.add(r0.mul(radius)).sub(normal.mul(band)), 0, 0);
                batch.corner(centre.add(r1.mul(radius)).sub(normal.mul(band)), 1, 0);
                batch.corner(centre.add(r1.mul(radius)).add(normal.mul(band)), 1, 1);
                batch.corner(centre.add(r0.mul(radius)).add(normal.mul(band)), 0, 1);
            }
        }
    }

    private static Vector3 onSphere(Vector3 centre, double radius, int stack, int slice) {
        double polar = Math.PI * stack / STACKS;
        double turn = Math.PI * 2 * slice / SLICES;
        return centre.add(new Vector3(Math.sin(polar) * Math.cos(turn), Math.cos(polar), Math.sin(polar) * Math.sin(turn)).mul(radius));
    }
}
