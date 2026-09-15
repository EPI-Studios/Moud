package com.meekdev.moud.mod.client.editor.assets;

import com.mojang.blaze3d.platform.NativeImage;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import foundry.imgui.impl.ImGuiMCImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class Thumbnails {

    private static final int MAX_ENTRIES = 256;
    private static final int LOADS_PER_FRAME = 4;
    private static final int LARGEST = 128;

    private record Loaded(DynamicTexture texture, ImGuiTextureProvider provider) {}

    private final Map<String, Loaded> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> failed = new HashSet<>();
    private int budget;

    void beginFrame() {
        budget = LOADS_PER_FRAME;
    }

    OptionalLong get(AssetEntry entry) {
        String key = entry.path() + "@" + entry.modified();
        if (failed.contains(key)) return OptionalLong.empty();
        Loaded loaded = cache.get(key);
        if (loaded == null) {
            if (budget <= 0) return OptionalLong.empty();
            budget--;
            loaded = load(entry.path());
            if (loaded == null) {
                failed.add(key);
                return OptionalLong.empty();
            }
            cache.put(key, loaded);
            trim();
        }
        return OptionalLong.of(ImGuiMCImpl.handler.getRenderer().getImGuiId(loaded.provider(), null));
    }

    void close() {
        for (Loaded loaded : cache.values()) loaded.texture().close();
        cache.clear();
        failed.clear();
    }

    private void trim() {
        Iterator<Map.Entry<String, Loaded>> iterator = cache.entrySet().iterator();
        while (cache.size() > MAX_ENTRIES && iterator.hasNext()) {
            iterator.next().getValue().texture().close();
            iterator.remove();
        }
    }

    private static Loaded load(Path path) {
        NativeImage image;
        try (InputStream in = Files.newInputStream(path)) {
            image = NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest > LARGEST) {
            int width = Math.max(1, image.getWidth() * LARGEST / longest);
            int height = Math.max(1, image.getHeight() * LARGEST / longest);
            NativeImage scaled = new NativeImage(width, height, false);
            image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);
            image.close();
            image = scaled;
        }
        DynamicTexture texture = new DynamicTexture(() -> "moud asset " + path.getFileName(), image);
        return new Loaded(texture, ImGuiMC.getTexture(texture));
    }
}
