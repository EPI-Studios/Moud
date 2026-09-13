package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.grade.ColorGradeSettings;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ShaderProgram;
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
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.post.AmbientOcclusionEffect;
import com.meekdev.moud.core.render.post.AntiAliasingEffect;
import com.meekdev.moud.core.render.post.BloomEffect;
import com.meekdev.moud.core.render.post.ColorGradeEffect;
import com.meekdev.moud.core.render.post.ContactShadowEffect;
import com.meekdev.moud.core.render.post.GlobalIlluminationEffect;
import com.meekdev.moud.core.render.post.PostEffect;
import com.meekdev.moud.core.render.post.PostShader;
import com.meekdev.moud.core.render.post.ReflectionEffect;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.core.render.post.ShadowQuality;
import com.meekdev.moud.core.render.post.VolumetricEffect;
import com.meekdev.moud.core.value.BoolValue;
import com.meekdev.moud.core.value.NumberValue;
import com.meekdev.moud.core.value.Vector3Value;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class PostStack {

    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier HEADER = Identifier.fromNamespaceAndPath("moud", "shaders/post/header.glsl");

    private static final Map<ClassDef<?>, String> BUILT_IN = new HashMap<>();

    static {
        BUILT_IN.put(Classes.VIGNETTE_EFFECT, "vignette");
        BUILT_IN.put(Classes.CHROMATIC_ABERRATION_EFFECT, "chromatic_aberration");
        BUILT_IN.put(Classes.FILM_GRAIN_EFFECT, "film_grain");
        BUILT_IN.put(Classes.BLUR_EFFECT, "blur");
        BUILT_IN.put(Classes.DEPTH_OF_FIELD_EFFECT, "depth_of_field");
        BUILT_IN.put(Classes.MOTION_BLUR_EFFECT, "motion_blur");
        BUILT_IN.put(Classes.PIXELATE_EFFECT, "pixelate");
        BUILT_IN.put(Classes.POSTERIZE_EFFECT, "posterize");
        BUILT_IN.put(Classes.SHARPEN_EFFECT, "sharpen");
        BUILT_IN.put(Classes.FOG_EFFECT, "fog");
        BUILT_IN.put(Classes.OUTLINE_EFFECT, "outline");
        BUILT_IN.put(Classes.TONEMAP_EFFECT, "tonemap");
    }

    private static final class Program {
        final ShaderProgram program;
        String source;
        long checked;
        boolean broken;

        Program(ShaderProgram program, String source) {
            this.program = program;
            this.source = source;
        }
    }

    private static final Map<String, Program> PROGRAMS = new HashMap<>();
    private static final Map<Identifier, String> SOURCES = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private static final Defaults DEFAULTS = new Defaults();
    private static final Set<Class<?>> APPLIED = new HashSet<>();

    private static final List<ScreenEffect> FRAME = new ArrayList<>();
    private static Framebuffer color;
    private static Framebuffer depth;
    private static Framebuffer out;
    private static final Matrix4f PREV_VIEW_PROJ = new Matrix4f();
    private static Vector3 prevEye = Vector3.ZERO;
    private static boolean hasPrev;
    private static final long START = System.nanoTime();

    private PostStack() {}

    public static void register() {
        DEFAULTS.capture();
        Pipeline.add(RenderStage.POST, 15, "moud screen effects", ctx -> draw());
    }

    public static void frame() {
        InstanceTree tree = ClientScene.tree();
        FRAME.clear();
        Set<Class<?>> seen = new HashSet<>();
        if (tree != null) {
            for (PostEffect effect : tree.ofClass(Classes.POST_EFFECT)) {
                if (!effect.enabled) continue;
                if (effect instanceof ScreenEffect screen) {
                    if (screen.intensity > 0) FRAME.add(screen);
                    continue;
                }
                if (!seen.add(effect.getClass())) continue;
                apply(effect);
            }
        }
        FRAME.sort(Comparator.comparingDouble(effect -> effect.order));
        for (Class<?> type : new ArrayList<>(APPLIED)) {
            if (seen.contains(type)) continue;
            DEFAULTS.restore(type);
            APPLIED.remove(type);
        }
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

    private static void draw() {
        if (FRAME.isEmpty()) return;
        CameraSnapshot camera = CameraSnapshot.current();
        if (camera == null) return;
        if (color == null) {
            color = Framebuffers.captureColor();
            depth = Framebuffers.captureDepth();
            out = Framebuffers.captureColor();
        }
        depth.blitDepthFromMain();
        float time = (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0);
        if (!hasPrev) {
            PREV_VIEW_PROJ.set(camera.viewProj);
            prevEye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        }
        for (ScreenEffect effect : FRAME) {
            Program program = program(effect);
            if (program == null || program.broken) continue;
            color.blitColorFromMain();
            GlState.beginFullscreen();
            try {
                out.begin();
                GlState.bindTexture(0, color.colorTextureGlId(0));
                GlState.bindTexture(1, depth.depthTextureGlId());
                ShaderProgram shader = program.program;
                shader.begin();
                shader.setSampler("SceneColorSampler", 0);
                shader.setSampler("SceneDepthSampler", 1);
                shader.setVec2("ScreenSize", color.width(), color.height());
                shader.setFloat("Time", time);
                shader.setVec3("CameraPosition", (float) camera.eye.x, (float) camera.eye.y, (float) camera.eye.z);
                shader.setVec3("PrevCameraPosition", (float) prevEye.x(), (float) prevEye.y(), (float) prevEye.z());
                shader.setMatrix4("ViewProj", camera.viewProj);
                shader.setMatrix4("InvViewProj", camera.invViewProj);
                shader.setMatrix4("PrevViewProj", PREV_VIEW_PROJ);
                shader.setInt("ZeroToOne", camera.zeroToOne ? 1 : 0);
                uniforms(effect, shader);
                shader.draw();
                out.end();
                out.blitColorToMain();
            } catch (RuntimeException e) {
                program.broken = true;
                if (WARNED.add(program.source)) MoudMod.LOG.warn("{} cannot be drawn: {}", effect.name(), e.getMessage());
            } finally {
                GlState.endFullscreen();
            }
        }
        PREV_VIEW_PROJ.set(camera.viewProj);
        prevEye = new Vector3(camera.eye.x, camera.eye.y, camera.eye.z);
        hasPrev = true;
    }

    private static void uniforms(ScreenEffect effect, ShaderProgram shader) {
        for (PropertyDef property : effect.def().properties()) {
            String name = Character.toUpperCase(property.name().charAt(0)) + property.name().substring(1);
            switch (property.type()) {
                case BOOL -> shader.setInt(name, property.getBool(effect) ? 1 : 0);
                case INT, NUM -> shader.setFloat(name, (float) property.getNum(effect));
                case VEC3 -> {
                    Vector3 v = (Vector3) property.getObj(effect);
                    shader.setVec3(name, (float) v.x(), (float) v.y(), (float) v.z());
                }
                case COLOR -> {
                    Color c = (Color) property.getObj(effect);
                    shader.setVec4(name, c.r(), c.g(), c.b(), c.a());
                }
                case ENUM -> shader.setInt(name, ((Enum<?>) property.getObj(effect)).ordinal());
                default -> {}
            }
        }
        if (!(effect instanceof PostShader)) return;
        for (Instance child : effect.children()) {
            switch (child) {
                case NumberValue n -> shader.setFloat(child.name(), (float) n.value);
                case BoolValue b -> shader.setInt(child.name(), b.value ? 1 : 0);
                case Vector3Value v -> shader.setVec3(child.name(), (float) v.value.x(), (float) v.value.y(), (float) v.value.z());
                default -> {}
            }
        }
    }

    private static @Nullable Program program(ScreenEffect effect) {
        String key;
        String body;
        if (effect instanceof PostShader custom) {
            if (custom.shader.isEmpty()) return null;
            key = custom.shader;
            Program known = PROGRAMS.get(key);
            long now = System.currentTimeMillis();
            if (known != null && now - known.checked < 1000) return known;
            body = read(custom.shader);
            if (body == null) {
                if (WARNED.add(key)) MoudMod.LOG.warn("post shader {} cannot be read", key);
                return known;
            }
            if (known != null) known.checked = now;
        } else {
            String file = BUILT_IN.get(effect.def());
            if (file == null) return null;
            key = "moud:" + file;
            Program known = PROGRAMS.get(key);
            if (known != null) return known;
            body = ShaderProgram.readSource(Identifier.fromNamespaceAndPath("moud", "shaders/post/" + file + ".glsl"));
        }
        String source = body.stripLeading().startsWith("#version") ? body : ShaderProgram.readSource(HEADER) + "\n" + body;
        Program known = PROGRAMS.get(key);
        if (known != null && known.source.equals(source)) return known;
        Identifier fragment = Identifier.fromNamespaceAndPath("moud",
                "shaders/post/generated/" + Integer.toHexString(key.hashCode()) + ".fsh");
        SOURCES.put(fragment, source);
        if (known == null) {
            ShaderProgram.registerVirtualSource(fragment, () -> SOURCES.get(fragment));
            known = new Program(new ShaderProgram(VERTEX, fragment), source);
            known.checked = System.currentTimeMillis();
            PROGRAMS.put(key, known);
        } else {
            known.source = source;
            known.broken = false;
            known.program.invalidate();
        }
        return known;
    }

    private static @Nullable String read(String shader) {
        if (shader.startsWith(Res.SCHEME)) {
            byte[] bytes = PlaceFiles.read(shader);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
        Identifier id = Identifier.tryParse(shader);
        if (id == null) return null;
        try {
            return ShaderProgram.readSource(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class Defaults {
        boolean bloomEnabled, bloomOcclude; float bloomIntensity, bloomThreshold, bloomKnee, bloomScale; int bloomLevels;
        boolean gradeEnabled; float exposure, contrast, saturation, brightness, temperature, tint, gamma, lutIntensity;
        Identifier lut; int lutSize;
        boolean ssaoEnabled, ssaoTemporal; float ssaoRadius, ssaoIntensity, ssaoBias, ssaoPower, ssaoScale;
        boolean ssgiEnabled; float ssgiRadius, ssgiIntensity, ssgiScale, ssgiHistory;
        boolean ssrEnabled, ssrTemporal; float ssrIntensity, ssrReflectivity, ssrDistance, ssrThickness, ssrEdge, ssrResolution; int ssrSteps;
        boolean taaEnabled; float taaFeedback, taaSharpness, taaClip;
        boolean volumetric, volumetricShadows; float volStrength, volDensity, volAniso, volScale; int volSteps;
        boolean contact; float contactDistance, contactThickness; int contactSteps;
        boolean shadows, pcss, entityShadows; int shadowRes, sunRes, cascades; float sunDistance, maxDistance, softness, lightSize;

        void capture() {
            BloomSettings b = Bloom.settings();
            bloomEnabled = b.isEnabled(); bloomOcclude = b.isOcclude(); bloomIntensity = b.intensity();
            bloomThreshold = b.threshold(); bloomKnee = b.knee(); bloomScale = b.scale(); bloomLevels = b.levels();
            ColorGradeSettings g = ColorGrade.settings();
            gradeEnabled = g.isEnabled(); exposure = g.exposure(); contrast = g.contrast(); saturation = g.saturation();
            brightness = g.brightness(); temperature = g.temperature(); tint = g.tint(); gamma = g.gamma();
            lut = g.lut(); lutSize = g.lutSize(); lutIntensity = g.lutIntensity();
            SsaoSettings a = Ssao.settings();
            ssaoEnabled = a.isEnabled(); ssaoTemporal = a.temporal(); ssaoRadius = a.radius(); ssaoIntensity = a.intensity();
            ssaoBias = a.bias(); ssaoPower = a.power(); ssaoScale = a.scale();
            SsgiSettings i = Ssgi.settings();
            ssgiEnabled = i.isEnabled(); ssgiRadius = i.radius(); ssgiIntensity = i.intensity(); ssgiScale = i.scale();
            ssgiHistory = i.historyBlend();
            SsrSettings r = Ssr.settings();
            ssrEnabled = r.isEnabled(); ssrTemporal = r.temporal(); ssrIntensity = r.intensity();
            ssrReflectivity = r.reflectivity(); ssrDistance = r.maxDistance(); ssrThickness = r.thickness();
            ssrEdge = r.edgeFade(); ssrResolution = r.resolution(); ssrSteps = r.maxSteps();
            TaaSettings t = Taa.settings();
            taaEnabled = t.isEnabled(); taaFeedback = t.feedback(); taaSharpness = t.sharpness(); taaClip = t.clipTightness();
            LightSettings l = LightSettings.defaults();
            volumetric = l.volumetric(); volumetricShadows = l.volumetricShadows(); volStrength = l.volumetricStrength();
            volDensity = l.volumetricDensity(); volAniso = l.volumetricAniso(); volScale = l.volumetricScale();
            volSteps = l.volumetricSteps();
            contact = l.contactShadows(); contactDistance = l.contactDistance(); contactThickness = l.contactThickness();
            contactSteps = l.contactSteps();
            ShadowSettings s = ShadowSettings.defaults();
            shadows = Shadows.isEnabled(); pcss = s.pcss(); entityShadows = s.entityShadows(); shadowRes = s.resolution();
            sunRes = s.sunResolution(); cascades = s.sunCascades(); sunDistance = s.sunDistance();
            maxDistance = s.maxDistance(); softness = s.softness(); lightSize = s.lightSize();
        }

        void restore(Class<?> type) {
            if (type == BloomEffect.class) {
                Bloom.settings().enabled(bloomEnabled).occlude(bloomOcclude).intensity(bloomIntensity)
                        .threshold(bloomThreshold).knee(bloomKnee).scale(bloomScale).levels(bloomLevels);
            } else if (type == ColorGradeEffect.class) {
                ColorGrade.settings().enabled(gradeEnabled).exposure(exposure).contrast(contrast)
                        .saturation(saturation).brightness(brightness).temperature(temperature).tint(tint)
                        .gamma(gamma).lut(lut).lutSize(lutSize).lutIntensity(lutIntensity);
            } else if (type == AmbientOcclusionEffect.class) {
                Ssao.settings().enabled(ssaoEnabled).temporal(ssaoTemporal).radius(ssaoRadius)
                        .intensity(ssaoIntensity).bias(ssaoBias).power(ssaoPower).scale(ssaoScale);
            } else if (type == GlobalIlluminationEffect.class) {
                Ssgi.settings().enabled(ssgiEnabled).radius(ssgiRadius).intensity(ssgiIntensity)
                        .scale(ssgiScale).historyBlend(ssgiHistory);
            } else if (type == ReflectionEffect.class) {
                Ssr.settings().enabled(ssrEnabled).temporal(ssrTemporal).intensity(ssrIntensity)
                        .reflectivity(ssrReflectivity).maxDistance(ssrDistance).thickness(ssrThickness)
                        .edgeFade(ssrEdge).resolution(ssrResolution).maxSteps(ssrSteps);
            } else if (type == AntiAliasingEffect.class) {
                Taa.settings().enabled(taaEnabled).feedback(taaFeedback).sharpness(taaSharpness).clipTightness(taaClip);
            } else if (type == VolumetricEffect.class) {
                LightSettings.defaults().volumetric(volumetric).volumetricShadows(volumetricShadows)
                        .volumetricStrength(volStrength).volumetricDensity(volDensity).volumetricAniso(volAniso)
                        .volumetricScale(volScale).volumetricSteps(volSteps);
            } else if (type == ContactShadowEffect.class) {
                LightSettings.defaults().contactShadows(contact).contactDistance(contactDistance)
                        .contactThickness(contactThickness).contactSteps(contactSteps);
            } else if (type == ShadowQuality.class) {
                Shadows.setEnabled(shadows);
                ShadowSettings.defaults().pcss(pcss).entityShadows(entityShadows).resolution(shadowRes)
                        .sunResolution(sunRes).sunCascades(cascades).sunDistance(sunDistance)
                        .maxDistance(maxDistance).softness(softness).lightSize(lightSize);
            }
        }
    }
}
