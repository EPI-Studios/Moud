package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.model.MaterialLook;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelLook;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.part.MaterialOverride;
import com.meekdev.moud.core.part.MeshPart;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.effect.EffectTextures;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.jspecify.annotations.Nullable;

final class MeshLooks {

    private static final Map<MeshPart, ModelLook> LOOKS = new WeakHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private MeshLooks() {}

    static String file(String meshId) {
        int piece = meshId.indexOf('#');
        return piece < 0 ? meshId : meshId.substring(0, piece);
    }

    static Model.@Nullable Piece piece(String meshId, Model model) {
        int at = meshId.indexOf('#');
        if (at < 0 || at == meshId.length() - 1) return null;
        String name = meshId.substring(at + 1);
        Model.Piece piece = model.piece(name);
        if (piece == null && WARNED.add(meshId)) {
            MoudMod.LOG.warn("{} has no node or mesh called {}, it has {}", file(meshId), name, model.pieceNames());
        }
        return piece;
    }

    // what this part changes on its model this frame, or null when it draws the file as it is
    static @Nullable ModelLook of(MeshPart part, Model model) {
        Model.Piece piece = piece(part.meshId, model);
        List<Instance> children = part.children();
        boolean overridden = false;
        for (Instance child : children) {
            if (child instanceof MaterialOverride) {
                overridden = true;
                break;
            }
        }
        if (piece == null && part.transparency <= 0 && !overridden) {
            LOOKS.remove(part);
            return null;
        }
        ModelLook look = LOOKS.computeIfAbsent(part, ignored -> new ModelLook()).reset().piece(piece);
        if (part.transparency > 0) look.all().opacity((float) (1 - part.transparency));
        for (Instance child : children) {
            if (child instanceof MaterialOverride override && override.material.isEmpty()) apply(override, look.all());
        }
        for (Instance child : children) {
            if (!(child instanceof MaterialOverride override) || override.material.isEmpty()) continue;
            if (model.material(override.material) == null) {
                if (WARNED.add(part.meshId + "@" + override.material)) {
                    MoudMod.LOG.warn("{} has no material called {}, it has {}", file(part.meshId), override.material,
                            model.materials().stream().map(ModelMaterial::name).toList());
                }
                continue;
            }
            apply(override, look.material(override.material));
        }
        return look;
    }

    private static void apply(MaterialOverride override, MaterialLook into) {
        Color tint = override.color;
        into.tint(tint.r(), tint.g(), tint.b())
                .opacity((float) (1 - override.transparency))
                .emissiveStrength((float) override.emissionStrength)
                .doubleSided(override.doubleSided);
        if (override.roughness >= 0) into.roughness((float) override.roughness);
        if (override.metallic >= 0) into.metallic((float) override.metallic);
        Color glow = override.emission;
        if (glow.r() > 0 || glow.g() > 0 || glow.b() > 0) into.emissive(glow.r(), glow.g(), glow.b());
        switch (override.filter) {
            case NEAREST -> into.filter(TextureFilter.NEAREST);
            case LINEAR -> into.filter(TextureFilter.LINEAR);
            case DEFAULT -> {}
        }
        if (!override.texture.isEmpty()) into.baseColorGlTexture(EffectTextures.of(override.texture).gl());
        if (!override.normalMap.isEmpty()) into.normalGlTexture(EffectTextures.map(override.normalMap, true).gl());
        if (!override.roughnessMap.isEmpty()) into.roughnessGlTexture(EffectTextures.map(override.roughnessMap, false).gl());
        if (!override.metallicMap.isEmpty()) into.metallicGlTexture(EffectTextures.map(override.metallicMap, false).gl());
        if (!override.occlusionMap.isEmpty()) into.occlusionGlTexture(EffectTextures.map(override.occlusionMap, false).gl());
        if (!override.emissionMap.isEmpty()) into.emissiveGlTexture(EffectTextures.map(override.emissionMap, false).gl());
        if (override.repeatU != 1 || override.repeatV != 1 || override.offsetU != 0 || override.offsetV != 0) {
            into.uv((float) override.repeatU, (float) override.repeatV, (float) override.offsetU, (float) override.offsetV);
        }
    }
}
