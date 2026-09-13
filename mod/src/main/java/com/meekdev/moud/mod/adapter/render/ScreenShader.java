package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.render.post.PostShader;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class ScreenShader {

    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier HEADER = Identifier.fromNamespaceAndPath("moud", "shaders/post/header.glsl");
    private static final long RECHECK_MILLIS = 1000;

    private static final Map<ClassDef<?>, String> BUILT_IN = Map.ofEntries(
            Map.entry(Classes.VIGNETTE_EFFECT, "vignette"),
            Map.entry(Classes.CHROMATIC_ABERRATION_EFFECT, "chromatic_aberration"),
            Map.entry(Classes.FILM_GRAIN_EFFECT, "film_grain"),
            Map.entry(Classes.BLUR_EFFECT, "blur"),
            Map.entry(Classes.DEPTH_OF_FIELD_EFFECT, "depth_of_field"),
            Map.entry(Classes.MOTION_BLUR_EFFECT, "motion_blur"),
            Map.entry(Classes.PIXELATE_EFFECT, "pixelate"),
            Map.entry(Classes.POSTERIZE_EFFECT, "posterize"),
            Map.entry(Classes.SHARPEN_EFFECT, "sharpen"),
            Map.entry(Classes.FOG_EFFECT, "fog"),
            Map.entry(Classes.OUTLINE_EFFECT, "outline"),
            Map.entry(Classes.TONEMAP_EFFECT, "tonemap"));

    private static final Map<String, ScreenShader> LOADED = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    final ShaderProgram program;
    private String source;
    private long checked = System.currentTimeMillis();
    private boolean broken;

    private ScreenShader(String key, String source) {
        this.source = source;
        Identifier fragment = Identifier.fromNamespaceAndPath("moud",
                "shaders/post/generated/" + Integer.toHexString(key.hashCode()) + ".fsh");
        ShaderProgram.registerVirtualSource(fragment, () -> this.source);
        this.program = new ShaderProgram(VERTEX, fragment);
    }

    boolean broken() {
        return broken;
    }

    void fail(String effect, RuntimeException e) {
        broken = true;
        if (WARNED.add(source)) MoudMod.LOG.warn("{} cannot be drawn: {}", effect, e.getMessage());
    }

    static @Nullable ScreenShader of(ScreenEffect effect) {
        if (effect instanceof PostShader custom) return custom.shader.isEmpty() ? null : custom(custom.shader);
        String file = BUILT_IN.get(effect.def());
        if (file == null) return null;
        String key = "moud:" + file;
        ScreenShader known = LOADED.get(key);
        if (known != null) return known;
        return load(key, ShaderProgram.readSource(Identifier.fromNamespaceAndPath("moud", "shaders/post/" + file + ".glsl")));
    }

    private static @Nullable ScreenShader custom(String path) {
        ScreenShader known = LOADED.get(path);
        long now = System.currentTimeMillis();
        if (known != null) {
            if (now - known.checked < RECHECK_MILLIS) return known;
            known.checked = now;
        }
        String body = read(path);
        if (body == null) {
            if (WARNED.add(path)) MoudMod.LOG.warn("post shader {} cannot be read", path);
            return known;
        }
        return load(path, body);
    }

    private static ScreenShader load(String key, String body) {
        String source = body.stripLeading().startsWith("#version") ? body : ShaderProgram.readSource(HEADER) + "\n" + body;
        ScreenShader known = LOADED.get(key);
        if (known == null) {
            known = new ScreenShader(key, source);
            LOADED.put(key, known);
        } else if (!known.source.equals(source)) {
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
}
