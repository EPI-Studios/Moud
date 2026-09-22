package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.material.SurfaceInputs;
import com.meekdev.amnetic.client.material.SurfaceLayout;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.core.value.BoolValue;
import com.meekdev.moud.core.value.NumberValue;
import com.meekdev.moud.core.value.StringValue;
import com.meekdev.moud.core.value.Vector3Value;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.effect.EffectTextures;
import com.meekdev.moud.mod.client.PlaceFiles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.jspecify.annotations.Nullable;

final class SurfaceShaders {

    private static final long RECHECK_MILLIS = 1000;

    private record Source(String text, long checkedAt) {}

    private record Texts(String surface, String lighting, String vertex) {}

    private record Compiled(Texts texts, @Nullable ShadingModel shading) {}

    private static final Map<String, Source> SOURCES = new HashMap<>();
    private static final Map<String, Compiled> COMPILED = new HashMap<>();
    private static final Map<MeshPart, SurfaceInputs> INPUTS = new WeakHashMap<>();

    private SurfaceShaders() {}

    static String key(MeshPart part) {
        if (part.shader.isEmpty() && part.lightingShader.isEmpty() && part.vertexShader.isEmpty()) return "";
        return part.shader + "|" + part.lightingShader + "|" + part.vertexShader;
    }

    // points every material of the part's copy of the model at its shaders, and returns the values
    // this draw hands them
    static @Nullable SurfaceInputs apply(MeshPart part, Model model) {
        ShadingModel shading = of(part);
        if (shading == null) return null;
        for (ModelMaterial material : model.materials()) {
            if (material.shadingModelId() != shading.id()) material.setShadingModel(shading);
        }
        if (shading.layout().slots().isEmpty()) return null;
        return inputs(part, shading.layout());
    }

    private static @Nullable ShadingModel of(MeshPart part) {
        String key = key(part);
        Texts texts = new Texts(text(part.shader), text(part.lightingShader), text(part.vertexShader));
        Compiled known = COMPILED.get(key);
        if (known != null && known.texts().equals(texts)) return known.shading();
        if (known != null && known.shading() != null) known.shading().release();
        ShadingModel shading = compile(texts, key);
        COMPILED.put(key, new Compiled(texts, shading));
        return shading;
    }

    private static @Nullable ShadingModel compile(Texts texts, String key) {
        if (texts.surface().isEmpty() && texts.lighting().isEmpty() && texts.vertex().isEmpty()) return null;
        try {
            ShadingModel shading = ShadingModel.pbr();
            if (!texts.lighting().isEmpty()) shading = shading.fragment(texts.lighting());
            if (!texts.surface().isEmpty()) shading = shading.surface(texts.surface());
            if (!texts.vertex().isEmpty()) shading = shading.vertex(texts.vertex());
            return shading;
        } catch (RuntimeException problem) {
            MoudMod.LOG.warn("the shaders {} cannot be used: {}", key, problem.getMessage());
            return null;
        }
    }

    private static SurfaceInputs inputs(MeshPart part, SurfaceLayout layout) {
        SurfaceInputs inputs = INPUTS.computeIfAbsent(part, ignored -> new SurfaceInputs());
        for (Map.Entry<String, SurfaceLayout.Slot> entry : layout.slots().entrySet()) {
            SurfaceLayout.Slot slot = entry.getValue();
            Instance child = part.child(entry.getKey());
            if (slot.kind() == SurfaceLayout.Kind.TEXTURE) {
                String image = child instanceof StringValue text ? text.value : "";
                inputs.texture(slot.index(), image.isEmpty() ? 0 : EffectTextures.of(image).gl());
                continue;
            }
            switch (child) {
                case NumberValue number -> inputs.set(slot.index(), (float) number.value, 0, 0, 0);
                case BoolValue flag -> inputs.set(slot.index(), flag.value ? 1 : 0, 0, 0, 0);
                case Vector3Value vector -> {
                    Vector3 v = vector.value;
                    inputs.set(slot.index(), (float) v.x(), (float) v.y(), (float) v.z(), 1);
                }
                case null, default -> inputs.set(slot.index(), 0, 0, 0, 0);
            }
        }
        return inputs;
    }

    private static String text(String file) {
        if (file.isEmpty()) return "";
        long now = System.currentTimeMillis();
        Source known = SOURCES.get(file);
        if (known != null && now - known.checkedAt() < RECHECK_MILLIS) return known.text();
        byte[] bytes = PlaceFiles.read(file);
        if (bytes == null) {
            if (known == null) MoudMod.LOG.warn("the shader {} is not in the place", file);
            String kept = known == null ? "" : known.text();
            SOURCES.put(file, new Source(kept, now));
            return kept;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        SOURCES.put(file, new Source(text, now));
        return text;
    }
}
