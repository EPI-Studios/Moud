package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Attachment;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.physics.Constraint;
import com.meekdev.moud.core.physics.HingeConstraint;
import com.meekdev.moud.core.physics.PrismaticConstraint;
import com.meekdev.moud.core.physics.RopeConstraint;
import com.meekdev.moud.core.physics.SpringConstraint;
import com.meekdev.moud.core.ui.ViewportFrame;

final class Ropes {

    private static final double THICKNESS = 0.05;
    private static final double AXIS_LENGTH = 0.6;
    private static final Color ROPE = new Color(0.55f, 0.42f, 0.28f, 1f);
    private static final Color SPRING = new Color(0.75f, 0.75f, 0.8f, 1f);
    private static final Color JOINT = new Color(0.95f, 0.6f, 1f, 1f);
    private static final double MIN_SEGMENT_SQ = 1.0e-8;
    private static final double MIN_SIDE_SQ = 1.0e-10;

    private Ropes() {}

    static void draw(QuadBatch batch, Effects.View view, InstanceTree tree) {
        boolean started = false;
        for (Instance instance : tree.ofClass(Classes.CONSTRAINT)) {
            if (!(instance instanceof Constraint constraint) || !constraint.visible || !constraint.enabled) continue;
            if (!(constraint.attachment0 instanceof Attachment a0) || !a0.isAlive() || ViewportFrame.inside(a0)) continue;
            if (!started) {
                batch.use(QuadBatch.Layer.WORLD, EffectTextures.white());
                batch.light(0, 0, 0);
                started = true;
            }
            CFrame at0 = Transforms.world(a0);
            if (constraint instanceof HingeConstraint || constraint instanceof PrismaticConstraint) {
                Vector3 axis = at0.rightVector().mul(AXIS_LENGTH);
                batch.tint(JOINT, 1, 1);
                segment(batch, view, at0.position().sub(axis), at0.position().add(axis));
            }
            if (!(constraint.attachment1 instanceof Attachment a1) || !a1.isAlive()) continue;
            batch.tint(colour(constraint), 1, 1);
            segment(batch, view, at0.position(), Transforms.world(a1).position());
        }
    }

    private static Color colour(Constraint constraint) {
        return switch (constraint) {
            case SpringConstraint ignored -> SPRING;
            case RopeConstraint ignored -> ROPE;
            default -> JOINT;
        };
    }

    private static void segment(QuadBatch batch, Effects.View view, Vector3 from, Vector3 to) {
        Vector3 along = to.sub(from);
        if (along.lengthSq() < MIN_SEGMENT_SQ) return;
        Vector3 facing = view.eye().sub(from.add(to).mul(0.5));
        Vector3 side = along.cross(facing);
        if (side.lengthSq() < MIN_SIDE_SQ) side = along.cross(view.up());
        side = side.normalize().mul(THICKNESS * 0.5);
        batch.corner(from.sub(side), 0, 0);
        batch.corner(from.add(side), 1, 0);
        batch.corner(to.add(side), 1, 1);
        batch.corner(to.sub(side), 0, 1);
    }
}
