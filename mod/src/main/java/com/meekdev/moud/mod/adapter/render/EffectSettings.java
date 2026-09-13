package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.meekdev.amnetic.client.ssao.Ssao;
import com.meekdev.amnetic.client.ssao.SsaoSettings;
import com.meekdev.amnetic.client.ssgi.Ssgi;
import com.meekdev.amnetic.client.ssgi.SsgiSettings;
import com.meekdev.amnetic.client.ssr.Ssr;
import com.meekdev.amnetic.client.ssr.SsrSettings;
import com.meekdev.amnetic.client.taa.Taa;
import com.meekdev.amnetic.client.taa.TaaSettings;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.render.post.AmbientOcclusionEffect;
import com.meekdev.moud.core.render.post.AntiAliasingEffect;
import com.meekdev.moud.core.render.post.BloomEffect;
import com.meekdev.moud.core.render.post.ColorGradeEffect;
import com.meekdev.moud.core.render.post.ContactShadowEffect;
import com.meekdev.moud.core.render.post.GlobalIlluminationEffect;
import com.meekdev.moud.core.render.post.PostEffect;
import com.meekdev.moud.core.render.post.ReflectionEffect;
import com.meekdev.moud.core.render.post.ShadowQuality;
import com.meekdev.moud.core.render.post.VolumetricEffect;
import com.meekdev.moud.mod.client.ClientPlace;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class EffectSettings {

    private static final Map<Class<?>, Runnable> DEFAULTS = new HashMap<>();
    private static final Set<Class<?>> APPLIED = new HashSet<>();

    private EffectSettings() {}

    static void captureDefaults() {
        DEFAULTS.put(BloomEffect.class, bloom());
        DEFAULTS.put(ColorGradeEffect.class, grade());
        DEFAULTS.put(AmbientOcclusionEffect.class, ssao());
        DEFAULTS.put(GlobalIlluminationEffect.class, ssgi());
        DEFAULTS.put(ReflectionEffect.class, ssr());
        DEFAULTS.put(AntiAliasingEffect.class, taa());
        DEFAULTS.put(VolumetricEffect.class, volumetric());
        DEFAULTS.put(ContactShadowEffect.class, contactShadows());
        DEFAULTS.put(ShadowQuality.class, shadows());
    }

    static void applyOnly(Iterable<PostEffect> effects) {
        Set<Class<?>> seen = new HashSet<>();
        for (PostEffect effect : effects) {
            if (seen.add(effect.getClass())) apply(effect);
        }
        for (Class<?> type : APPLIED) {
            if (!seen.contains(type)) DEFAULTS.getOrDefault(type, () -> {}).run();
        }
        APPLIED.clear();
        APPLIED.addAll(seen);
    }

    private static void apply(PostEffect effect) {
        switch (effect) {
            case BloomEffect b -> Bloom.settings().enabled(true).intensity((float) b.intensity)
                    .threshold((float) b.threshold).knee((float) b.knee).levels(b.size)
                    .scale((float) b.resolution).occlude(b.occlude);
            case ColorGradeEffect g -> ColorGrade.settings().enabled(true).exposure((float) g.exposure)
                    .contrast((float) g.contrast).saturation((float) g.saturation)
                    .brightness((float) g.brightness).temperature((float) g.temperature).tint((float) g.tint)
                    .gamma((float) g.gamma).lut(lut(g.lut)).lutSize(g.lutSize).lutIntensity((float) g.lutIntensity);
            case AmbientOcclusionEffect a -> Ssao.settings().enabled(true).radius((float) a.radius)
                    .intensity((float) a.intensity).bias((float) a.bias).power((float) a.power)
                    .scale((float) a.resolution).temporal(a.temporal);
            case GlobalIlluminationEffect g -> Ssgi.settings().enabled(true).radius((float) g.radius)
                    .intensity((float) g.intensity).scale((float) g.resolution).historyBlend((float) g.history);
            case ReflectionEffect r -> Ssr.settings().enabled(true).intensity((float) r.intensity)
                    .reflectivity((float) r.reflectivity).maxDistance((float) r.maxDistance).maxSteps(r.steps)
                    .thickness((float) r.thickness).edgeFade((float) r.edgeFade)
                    .resolution((float) r.resolution).temporal(r.temporal);
            case AntiAliasingEffect t -> Taa.settings().enabled(true).feedback((float) t.history)
                    .sharpness((float) t.sharpness).clipTightness((float) t.clip);
            case VolumetricEffect v -> LightSettings.defaults().volumetric(true)
                    .volumetricStrength((float) v.strength).volumetricDensity((float) v.density)
                    .volumetricAniso((float) v.anisotropy).volumetricSteps(v.steps)
                    .volumetricScale((float) v.resolution).volumetricShadows(v.shadows);
            case ContactShadowEffect c -> LightSettings.defaults().contactShadows(true)
                    .contactDistance((float) c.distance).contactThickness((float) c.thickness)
                    .contactSteps(c.steps);
            case ShadowQuality s -> {
                Shadows.enable();
                ShadowSettings.defaults().resolution(s.resolution).sunResolution(s.sunResolution)
                        .sunCascades(s.sunCascades).sunDistance((float) s.sunDistance)
                        .maxDistance((float) s.maxDistance).softness((float) s.softness)
                        .pcss(s.contactHardening).lightSize((float) s.lightSize).entityShadows(s.entities);
            }
            default -> {}
        }
    }

    private static @Nullable Identifier lut(String lut) {
        if (lut.isEmpty()) return null;
        if (lut.startsWith(Res.SCHEME)) {
            Path root = ClientPlace.root();
            return root == null ? null : ImportedTextures.idForPath(root.resolve(Res.parse(lut)).toAbsolutePath().toString());
        }
        return Identifier.tryParse(lut);
    }

    private static Runnable bloom() {
        BloomSettings s = Bloom.settings();
        boolean enabled = s.isEnabled();
        boolean occlude = s.isOcclude();
        float intensity = s.intensity();
        float threshold = s.threshold();
        float knee = s.knee();
        float scale = s.scale();
        int levels = s.levels();
        return () -> Bloom.settings().enabled(enabled).occlude(occlude).intensity(intensity)
                .threshold(threshold).knee(knee).scale(scale).levels(levels);
    }

    private static Runnable grade() {
        ColorGradeSettings s = ColorGrade.settings();
        boolean enabled = s.isEnabled();
        float exposure = s.exposure();
        float contrast = s.contrast();
        float saturation = s.saturation();
        float brightness = s.brightness();
        float temperature = s.temperature();
        float tint = s.tint();
        float gamma = s.gamma();
        Identifier lut = s.lut();
        int lutSize = s.lutSize();
        float lutIntensity = s.lutIntensity();
        return () -> ColorGrade.settings().enabled(enabled).exposure(exposure).contrast(contrast)
                .saturation(saturation).brightness(brightness).temperature(temperature).tint(tint)
                .gamma(gamma).lut(lut).lutSize(lutSize).lutIntensity(lutIntensity);
    }

    private static Runnable ssao() {
        SsaoSettings s = Ssao.settings();
        boolean enabled = s.isEnabled();
        boolean temporal = s.temporal();
        float radius = s.radius();
        float intensity = s.intensity();
        float bias = s.bias();
        float power = s.power();
        float scale = s.scale();
        return () -> Ssao.settings().enabled(enabled).temporal(temporal).radius(radius)
                .intensity(intensity).bias(bias).power(power).scale(scale);
    }

    private static Runnable ssgi() {
        SsgiSettings s = Ssgi.settings();
        boolean enabled = s.isEnabled();
        float radius = s.radius();
        float intensity = s.intensity();
        float scale = s.scale();
        float history = s.historyBlend();
        return () -> Ssgi.settings().enabled(enabled).radius(radius).intensity(intensity)
                .scale(scale).historyBlend(history);
    }

    private static Runnable ssr() {
        SsrSettings s = Ssr.settings();
        boolean enabled = s.isEnabled();
        boolean temporal = s.temporal();
        float intensity = s.intensity();
        float reflectivity = s.reflectivity();
        float distance = s.maxDistance();
        float thickness = s.thickness();
        float edge = s.edgeFade();
        float resolution = s.resolution();
        int steps = s.maxSteps();
        return () -> Ssr.settings().enabled(enabled).temporal(temporal).intensity(intensity)
                .reflectivity(reflectivity).maxDistance(distance).thickness(thickness)
                .edgeFade(edge).resolution(resolution).maxSteps(steps);
    }

    private static Runnable taa() {
        TaaSettings s = Taa.settings();
        boolean enabled = s.isEnabled();
        float feedback = s.feedback();
        float sharpness = s.sharpness();
        float clip = s.clipTightness();
        return () -> Taa.settings().enabled(enabled).feedback(feedback).sharpness(sharpness).clipTightness(clip);
    }

    private static Runnable volumetric() {
        LightSettings s = LightSettings.defaults();
        boolean enabled = s.volumetric();
        boolean shadows = s.volumetricShadows();
        float strength = s.volumetricStrength();
        float density = s.volumetricDensity();
        float aniso = s.volumetricAniso();
        float scale = s.volumetricScale();
        int steps = s.volumetricSteps();
        return () -> LightSettings.defaults().volumetric(enabled).volumetricShadows(shadows)
                .volumetricStrength(strength).volumetricDensity(density).volumetricAniso(aniso)
                .volumetricScale(scale).volumetricSteps(steps);
    }

    private static Runnable contactShadows() {
        LightSettings s = LightSettings.defaults();
        boolean enabled = s.contactShadows();
        float distance = s.contactDistance();
        float thickness = s.contactThickness();
        int steps = s.contactSteps();
        return () -> LightSettings.defaults().contactShadows(enabled).contactDistance(distance)
                .contactThickness(thickness).contactSteps(steps);
    }

    private static Runnable shadows() {
        ShadowSettings s = ShadowSettings.defaults();
        boolean enabled = Shadows.isEnabled();
        boolean pcss = s.pcss();
        boolean entities = s.entityShadows();
        int resolution = s.resolution();
        int sunResolution = s.sunResolution();
        int cascades = s.sunCascades();
        float sunDistance = s.sunDistance();
        float maxDistance = s.maxDistance();
        float softness = s.softness();
        float lightSize = s.lightSize();
        return () -> {
            Shadows.setEnabled(enabled);
            ShadowSettings.defaults().pcss(pcss).entityShadows(entities).resolution(resolution)
                    .sunResolution(sunResolution).sunCascades(cascades).sunDistance(sunDistance)
                    .maxDistance(maxDistance).softness(softness).lightSize(lightSize);
        };
    }
}
