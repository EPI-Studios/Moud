package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.EffectRuns;
import com.meekdev.moud.core.effect.Tally;
import com.meekdev.moud.core.effect.Trail;
import com.meekdev.moud.core.effect.TrailPath;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.ViewportFrame;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;

final class Trails {

    private static final double MIN_SIDE_SQ = 1.0e-12;

    private static final class Following {
        final TrailPath path = new TrailPath();
        final Tally clears;
        Vector3 a;
        Vector3 b;
        int light;

        Following(Trail trail) {
            clears = new Tally(trail.clears);
        }
    }

    private static final EffectRuns<Trail, Following> FOLLOWING = new EffectRuns<>(Following::new);

    private Trails() {}

    static void step(InstanceTree tree, double now, float partialTick) {
        FOLLOWING.begin(tree);
        for (Trail trail : tree.ofClass(Classes.TRAIL)) {
            Following following = FOLLOWING.run(trail);
            boolean requested = trail.takeClearRequest();
            if (following.clears.take(trail.clears) > 0 || requested) following.path.clear();
            if (ViewportFrame.inside(trail) || !Beams.attached(trail.attachment0) || !Beams.attached(trail.attachment1)) {
                following.a = null;
                following.path.clear();
                continue;
            }
            following.a = Effects.world(trail.attachment0, partialTick).position();
            following.b = Effects.world(trail.attachment1, partialTick).position();
            following.light = Effects.light(following.a.lerp(following.b, 0.5));
            TrailPath path = following.path;
            if (trail.enabled) path.follow(following.a, following.b, now, trail.minLength);
            path.expire(now, trail.lifetime);
            path.limit(trail.maxLength, following.a.lerp(following.b, 0.5));
        }
        FOLLOWING.end();
    }

    static void draw(QuadBatch batch, Effects.View view, double now) {
        for (Map.Entry<Trail, Following> entry : FOLLOWING.runs().entrySet()) {
            Trail trail = entry.getKey();
            Following following = entry.getValue();
            List<TrailPath.Point> points = following.path.points();
            if (following.a == null || points.isEmpty() || trail.lifetime <= 0) continue;
            int count = points.size() + (trail.enabled ? 1 : 0);
            if (count < 2) continue;
            TrailPath.Point[] ordered = new TrailPath.Point[count];
            int at = 0;
            if (trail.enabled) {
                TrailPath.Point last = points.getLast();
                double travelled = following.path.travelled() + following.a.lerp(following.b, 0.5).distance(last.middle());
                ordered[at++] = new TrailPath.Point(following.a, following.b, now, travelled);
            }
            for (int n = points.size() - 1; n >= 0; n--) ordered[at++] = points.get(n);
            batch.use(QuadBatch.Layer.WORLD, EffectTextures.of(trail.texture));
            batch.light(following.light, trail.lightInfluence, trail.lightEmission);
            draw(batch, view, trail, ordered, now);
        }
    }

    private static void draw(QuadBatch batch, Effects.View view, Trail trail, TrailPath.Point[] points, double now) {
        double head = points[0].travelled();
        double total = head - points[points.length - 1].travelled();
        Vector3[] left = new Vector3[points.length];
        Vector3[] right = new Vector3[points.length];
        double[] ages = new double[points.length];
        for (int n = 0; n < points.length; n++) {
            TrailPath.Point point = points[n];
            double t = Math.clamp((now - point.time()) / trail.lifetime, 0, 1);
            ages[n] = t;
            Vector3 middle = point.middle();
            double scale = Mth.lerp(t, trail.widthScaleStart, trail.widthScaleEnd);
            Vector3 half = point.b().sub(middle);
            if (trail.faceCamera) {
                Vector3 newer = points[Math.max(n - 1, 0)].middle();
                Vector3 older = points[Math.min(n + 1, points.length - 1)].middle();
                Vector3 side = newer.sub(older).cross(view.eye().sub(middle));
                half = side.lengthSq() < MIN_SIDE_SQ ? half : side.normalize().mul(half.length());
            }
            half = half.mul(scale);
            left[n] = middle.sub(half);
            right[n] = middle.add(half);
        }
        for (int n = 0; n < points.length - 1; n++) {
            double v0 = textureV(trail, head, total, points[n].travelled());
            double v1 = textureV(trail, head, total, points[n + 1].travelled());
            tint(batch, trail, ages[n]);
            batch.corner(left[n], 0, v0);
            batch.corner(right[n], 1, v0);
            tint(batch, trail, ages[n + 1]);
            batch.corner(right[n + 1], 1, v1);
            batch.corner(left[n + 1], 0, v1);
        }
    }

    private static double textureV(Trail trail, double head, double total, double travelled) {
        return switch (trail.textureMode) {
            case STRETCH -> total <= 0 ? 0 : (head - travelled) / total * trail.textureLength;
            case WRAP -> (head - travelled) / trail.textureLength;
            case STATIC -> travelled / trail.textureLength;
        };
    }

    private static void tint(QuadBatch batch, Trail trail, double t) {
        Color color = trail.colorStart.lerp(trail.colorEnd, (float) t);
        double transparency = Mth.lerp(t, trail.transparencyStart, trail.transparencyEnd);
        batch.tint(color, 1 - transparency, trail.brightness);
    }
}
