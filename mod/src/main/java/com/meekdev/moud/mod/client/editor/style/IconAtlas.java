package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.mod.MoudMod;
import com.mojang.blaze3d.platform.NativeImage;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import foundry.imgui.impl.ImGuiMCImpl;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jspecify.annotations.Nullable;

public final class IconAtlas {

    private final Map<EditorIcon, ImGuiTextureProvider> textures = new EnumMap<>(EditorIcon.class);
    private final Map<String, ImGuiTextureProvider> images = new HashMap<>();

    private static final String LOGO = "/assets/moud/editor/logo.png";

    private @Nullable ImGuiTextureProvider logo;

    public long logoTextureId() {
        if (logo == null) logo = uploadPath(LOGO, "logo");
        return ImGuiMCImpl.handler.getRenderer().getImGuiId(logo, null);
    }

    public long imageId(String resourcePath) {
        ImGuiTextureProvider provider = images.computeIfAbsent(resourcePath, path -> uploadPath(path, path));
        return ImGuiMCImpl.handler.getRenderer().getImGuiId(provider, null);
    }

    public long textureId(EditorIcon icon) {
        ImGuiTextureProvider provider = textures.computeIfAbsent(icon, IconAtlas::upload);
        return ImGuiMCImpl.handler.getRenderer().getImGuiId(provider, null);
    }

    private static ImGuiTextureProvider upload(EditorIcon icon) {
        return uploadPath(icon.resourcePath(), icon.name());
    }

    private static ImGuiTextureProvider uploadPath(String path, String name) {
        NativeImage image;
        try (InputStream in = IconAtlas.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            image = NativeImage.read(in);
        } catch (IOException e) {
            MoudMod.LOG.error("editor image {} could not be loaded", path, e);
            image = new NativeImage(1, 1, true);
        }
        return ImGuiMC.getTexture(new DynamicTexture(() -> "moud editor " + name, image));
    }
}
