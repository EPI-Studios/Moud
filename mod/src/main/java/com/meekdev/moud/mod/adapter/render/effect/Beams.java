package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.Beam;
import com.meekdev.moud.core.effect.BeamCurve;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.ViewportFrame;
import net.minecraft.util.Mth;

final class Beams {

    private static final int MAX_SEGMENTS = 1000;
    private static final double MIN_LENGTH = 1.0e-6;
    private static final double MIN_TANGENT_SQ = 1.0e-12;
    private static final double MIN_SIDE_SQ = 1.0e-10;

    private Beams() {}

    static void draw(QuadBatch batch, Effects.View view, InstanceTree tree, float partialTick, double time) {
        for (Beam beam : tree.ofClass(Classes.BEAM)) {
            if (!beam.enabled || !attached(beam.attachment0) || !attached(beam.attachment1)) continue;
            if (ViewportFrame.inside(beam)) continue;
            CFrame from = Effects.world(beam.attachment0, partialTick);
            CFrame to = Effects.world(beam.attachment1, partialTick);
            draw(batch, view, beam, from, to, time);
        }
    }

    static boolean attached(Instance attachment) {
        return attachment instanceof Spatial && attachment.isAlive();
    }

    private static void draw(QuadBatch batch, Effects.View view, Beam beam, CFrame from, CFrame to, double time) {
        int segments = Math.clamp(beam.segments, 1, MAX_SEGMENTS);
        Vector3[] points = BeamCurve.points(from, to, beam.curveSize0, beam.curveSize1, segments);
        double[] along = BeamCurve.distances(points);
        double total = along[segments];
        if (total < MIN_LENGTH) return;
        double scroll = time * beam.textureSpeed;
        batch.use(QuadBatch.Layer.WORLD, EffectTextures.of(beam.texture));
        batch.light(Effects.light(from.position().lerp(to.position(), 0.5)), beam.lightInfluence, beam.lightEmission);
        Vector3 straight = to.position().sub(from.position()).normalize();
        Vector3[] sides = new Vector3[segments + 1];
        for (int n = 0; n <= segments; n++) {
            double t = n / (double) segments;
            Vector3 next = points[Math.min(n + 1, segments)];
            Vector3 previous = points[Math.max(n - 1, 0)];
            Vector3 tangent = next.sub(previous);
            tangent = tangent.lengthSq() < MIN_TANGENT_SQ ? straight : tangent.normalize();
            Vector3 facing = beam.faceCamera ? view.eye().sub(points[n]) : from.upVector().lerp(to.upVector(), t);
            Vector3 side = tangent.cross(facing);
            if (side.lengthSq() < MIN_SIDE_SQ) side = tangent.cross(from.lookVector());
            double halfWidth = Mth.lerp(t, beam.width0, beam.width1) * 0.5;
            sides[n] = side.normalize().mul(halfWidth);
        }
        for (int n = 0; n < segments; n++) {
            double t0 = n / (double) segments;
            double t1 = (n + 1) / (double) segments;
            double v0 = BeamCurve.textureV(beam.textureMode, along[n], total, beam.textureLength, scroll);
            double v1 = BeamCurve.textureV(beam.textureMode, along[n + 1], total, beam.textureLength, scroll);
            tint(batch, beam, t0);
            batch.corner(points[n].sub(sides[n]), 0, v0);
            batch.corner(points[n].add(sides[n]), 1, v0);
            tint(batch, beam, t1);
            batch.corner(points[n + 1].add(sides[n + 1]), 1, v1);
            batch.corner(points[n + 1].sub(sides[n + 1]), 0, v1);
        }
    }

    private static void tint(QuadBatch batch, Beam beam, double t) {
        Color color = beam.colorStart.lerp(beam.colorEnd, (float) t);
        double transparency = Mth.lerp(t, beam.transparencyStart, beam.transparencyEnd);
        batch.tint(color, 1 - transparency, beam.brightness);
    }
}
