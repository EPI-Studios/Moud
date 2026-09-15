package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.mod.MoudMod;
import com.mojang.blaze3d.platform.NativeImage;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class IconAtlas {

    private final Map<EditorIcon, ImGuiTextureProvider> textures = new EnumMap<>(EditorIcon.class);

    public long textureId(EditorIcon icon) {
        ImGuiTextureProvider provider = textures.computeIfAbsent(icon, IconAtlas::upload);
        return provider == null ? 0L : provider.imguimc$id();
    }

    private static ImGuiTextureProvider upload(EditorIcon icon) {
        try (InputStream in = IconAtlas.class.getResourceAsStream(icon.resourcePath())) {
            if (in == null) throw new IOException("missing icon " + icon.resourcePath());
            DynamicTexture texture = new DynamicTexture(() -> "moud editor icon " + icon.name(), NativeImage.read(in));
            return ImGuiMC.getTexture(texture);
        } catch (IOException e) {
            MoudMod.LOG.error("editor icon {} could not be loaded", icon.resourcePath(), e);
            return null;
        }
    }
}
