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
        return get(entry.path(), entry.path() + "@" + entry.modified());
    }

    OptionalLong get(Path file, String key) {
        return get(() -> Files.newInputStream(file), key, false);
    }

    OptionalLong get(Opener opener, String key, boolean firstFrame) {
        if (failed.contains(key)) return OptionalLong.empty();
        Loaded loaded = cache.get(key);
        if (loaded == null) {
            if (budget <= 0) return OptionalLong.empty();
            budget--;
            loaded = load(opener, key, firstFrame);
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

    interface Opener {
        InputStream open() throws IOException;
    }

    private static Loaded load(Opener opener, String key, boolean firstFrame) {
        NativeImage image;
        try (InputStream in = opener.open()) {
            image = NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (firstFrame && image.getHeight() > image.getWidth() && image.getHeight() % image.getWidth() == 0) {
            NativeImage frame = new NativeImage(image.getWidth(), image.getWidth(), false);
            image.copyRect(frame, 0, 0, 0, 0, image.getWidth(), image.getWidth(), false, false);
            image.close();
            image = frame;
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
        DynamicTexture texture = new DynamicTexture(() -> "moud asset " + key, image);
        return new Loaded(texture, ImGuiMC.getTexture(texture));
    }
}
