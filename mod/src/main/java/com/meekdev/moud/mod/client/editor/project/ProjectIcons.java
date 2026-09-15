package com.meekdev.moud.mod.client.editor.project;

import com.mojang.blaze3d.platform.NativeImage;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import foundry.imgui.impl.ImGuiMCImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class ProjectIcons {

    public static final List<String> CANDIDATE_FILENAMES = List.of("icon.png", ".moud/icon.png");

    private final Map<Path, Optional<DynamicTexture>> textures = new HashMap<>();

    public Optional<Long> of(Path projectRoot) {
        return textures.computeIfAbsent(projectRoot.toAbsolutePath(), ProjectIcons::load)
                .map(texture -> ImGuiMCImpl.handler.getRenderer().getImGuiId((ImGuiTextureProvider) ImGuiMC.getTexture(texture), null));
    }

    public void forget(Path projectRoot) {
        Optional<DynamicTexture> texture = textures.remove(projectRoot.toAbsolutePath());
        if (texture != null) texture.ifPresent(DynamicTexture::close);
    }

    public void dispose() {
        textures.values().forEach(texture -> texture.ifPresent(DynamicTexture::close));
        textures.clear();
    }

    private static Optional<DynamicTexture> load(Path root) {
        for (String candidate : CANDIDATE_FILENAMES) {
            Path file = root.resolve(candidate);
            if (!Files.isRegularFile(file)) continue;
            try (InputStream in = Files.newInputStream(file)) {
                return Optional.of(new DynamicTexture(() -> "moud project icon " + root, NativeImage.read(in)));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
