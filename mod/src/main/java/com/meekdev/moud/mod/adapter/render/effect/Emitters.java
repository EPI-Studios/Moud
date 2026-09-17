package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.effect.EffectRuns;
import com.meekdev.moud.core.effect.Fire;
import com.meekdev.moud.core.effect.ParticleEmitter;
import com.meekdev.moud.core.effect.ParticleField;
import com.meekdev.moud.core.effect.ParticleLook;
import com.meekdev.moud.core.effect.Smoke;
import com.meekdev.moud.core.effect.Sparkles;
import com.meekdev.moud.core.effect.Tally;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.ViewportFrame;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;

final class Emitters {

    private static final double MIN_SIDE_SQ = 1.0e-8;
    private static final double MIN_SPEED_SQ = 1.0e-10;
    private static final double NEAR_VERTICAL = 0.99;

    private static final class Running {
        final ParticleField field = new ParticleField(new Random());
        final Tally emitted;
        ParticleLook look;
        CFrame frame = CFrame.IDENTITY;
        int light;

        Running(Instance source) {
            emitted = new Tally(source instanceof ParticleEmitter emitter ? emitter.emitted : 0);
        }
    }

    private static final List<ClassDef<? extends Instance>> SOURCES =
            List.of(Classes.PARTICLE_EMITTER, Classes.FIRE, Classes.SMOKE, Classes.SPARKLES);

    private static final EffectRuns<Instance, Running> RUNNING = new EffectRuns<>(Running::new);

    private Emitters() {}

    static void step(InstanceTree tree, double seconds, float partialTick) {
        RUNNING.begin(tree);
        for (ClassDef<? extends Instance> def : SOURCES) {
            for (Instance source : tree.ofClass(def)) step(source, RUNNING.run(source), seconds, partialTick);
        }
        RUNNING.end();
    }

    private static void step(Instance source, Running run, double seconds, float partialTick) {
        int burst = 0;
        if (source instanceof ParticleEmitter emitter) burst = run.emitted.take(emitter.emitted) + emitter.takeQueued();
        if (ViewportFrame.inside(source)) {
            run.field.clear();
            return;
        }
        ParticleLook look = look(source);
        run.look = look;
        Instance holder = source.parent();
        boolean placed = holder instanceof Spatial;
        if (placed) {
            run.frame = Effects.world(holder, partialTick);
            run.light = Effects.light(run.frame.position());
        }
        double scaled = seconds * look.timeScale();
        run.field.step(look, run.frame, scaled);
        int steady = run.field.due(enabled(source) && placed ? look.rate() : 0, scaled);
        if (!placed) return;
        Vector3 volume = holder instanceof Part part ? part.size : null;
        run.field.emit(look, run.frame, volume, Math.min(ParticleField.LIMIT, steady + burst));
    }

    private static boolean enabled(Instance source) {
        return switch (source) {
            case ParticleEmitter emitter -> emitter.enabled;
            case Fire fire -> fire.enabled;
            case Smoke smoke -> smoke.enabled;
            case Sparkles sparkles -> sparkles.enabled;
            default -> false;
        };
    }

    private static ParticleLook look(Instance source) {
        return switch (source) {
            case Fire fire -> ParticleLook.of(fire);
            case Smoke smoke -> ParticleLook.of(smoke);
            case Sparkles sparkles -> ParticleLook.of(sparkles);
            default -> ParticleLook.of((ParticleEmitter) source);
        };
    }

    static void draw(QuadBatch batch, Effects.View view) {
        for (Running run : RUNNING.runs().values()) {
            ParticleField field = run.field;
            if (field.count() == 0) continue;
            ParticleLook look = run.look;
            batch.use(QuadBatch.Layer.WORLD, EffectTextures.of(look.texture()));
            batch.light(run.light, look.lightInfluence(), look.lightEmission());
            for (int n = 0; n < field.count(); n++) {
                double t = field.progress(n);
                double half = Mth.lerp(t, look.sizeStart(), look.sizeEnd()) * 0.5;
                if (half <= 0) continue;
                Color color = look.colorStart().lerp(look.colorEnd(), (float) t);
                double transparency = Mth.lerp(t, look.transparencyStart(), look.transparencyEnd());
                batch.tint(color, 1 - transparency, look.brightness());
                Vector3 at = field.position(n, look, run.frame);
                Vector3 velocity = field.velocity(n, look, run.frame);
                double angle = Math.toRadians(field.rotation(n));
                quad(batch, view, look, velocity, at, half, angle);
            }
        }
    }

    private static void quad(QuadBatch batch, Effects.View view, ParticleLook look, Vector3 velocity, Vector3 at,
                             double half, double angle) {
        Vector3 right;
        Vector3 up;
        switch (look.orientation()) {
            case FACING_CAMERA_WORLD_UP -> {
                right = unitOr(view.forward().cross(Vector3.UP), MIN_SIDE_SQ, view.right());
                up = Vector3.UP;
            }
            case VELOCITY_PARALLEL -> {
                if (velocity.lengthSq() < MIN_SPEED_SQ) {
                    right = view.right();
                    up = view.up();
                } else {
                    up = velocity.normalize();
                    right = unitOr(up.cross(view.eye().sub(at)), MIN_SIDE_SQ, view.right());
                }
            }
            case VELOCITY_PERPENDICULAR -> {
                Vector3 facing = unitOr(velocity, MIN_SPEED_SQ, Vector3.UP);
                Vector3 reference = Math.abs(facing.y()) < NEAR_VERTICAL ? Vector3.UP : Vector3.RIGHT;
                right = facing.cross(reference).normalize();
                up = right.cross(facing);
            }
            default -> {
                right = view.right();
                up = view.up();
            }
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector3 across = right.mul(cos * half).add(up.mul(sin * half));
        Vector3 along = up.mul(cos * half).sub(right.mul(sin * half));
        batch.corner(at.sub(across).sub(along), 0, 1);
        batch.corner(at.add(across).sub(along), 1, 1);
        batch.corner(at.add(across).add(along), 1, 0);
        batch.corner(at.sub(across).add(along), 0, 0);
    }

    private static Vector3 unitOr(Vector3 direction, double minLengthSq, Vector3 fallback) {
        return direction.lengthSq() < minLengthSq ? fallback : direction.normalize();
    }
}
