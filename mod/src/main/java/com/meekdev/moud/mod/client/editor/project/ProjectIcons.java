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

    public record Image(long textureId, int width, int height) {

        public float aspect() {
            return height == 0 ? 1.0f : width / (float) height;
        }
    }

    private record Loaded(DynamicTexture texture, int width, int height) {}

    private final Map<Path, Optional<Loaded>> images = new HashMap<>();

    public Optional<Image> of(Path projectRoot) {
        return images.computeIfAbsent(projectRoot.toAbsolutePath(), ProjectIcons::load)
                .map(loaded -> new Image(textureId(loaded.texture()), loaded.width(), loaded.height()));
    }

    public void forget(Path projectRoot) {
        Optional<Loaded> loaded = images.remove(projectRoot.toAbsolutePath());
        if (loaded != null) loaded.ifPresent(one -> one.texture().close());
    }

    public void dispose() {
        images.values().forEach(loaded -> loaded.ifPresent(one -> one.texture().close()));
        images.clear();
    }

    private static long textureId(DynamicTexture texture) {
        return ImGuiMCImpl.handler.getRenderer().getImGuiId((ImGuiTextureProvider) ImGuiMC.getTexture(texture), null);
    }

    private static Optional<Loaded> load(Path root) {
        for (String candidate : CANDIDATE_FILENAMES) {
            Path file = root.resolve(candidate);
            if (!Files.isRegularFile(file)) continue;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                return Optional.of(new Loaded(new DynamicTexture(() -> "moud project icon " + root, image), image.getWidth(), image.getHeight()));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
