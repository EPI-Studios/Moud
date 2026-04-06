package com.moud.client.fabric.editor.panels;

import com.miry.graphics.Texture;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.AssetType;
import com.moud.net.protocol.AssetManifestResponse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

final class AssetThumbnails {
    private static final Map<AssetHash, CachedThumb> cache = new HashMap<>();

    private AssetThumbnails() {}

    static Texture get(AssetManifestResponse.Entry entry) {
        if (entry == null || entry.meta() == null) return null;
        if (entry.meta().type() != AssetType.IMAGE) return null;
        if (entry.path() == null) return null;

        AssetHash hash = entry.meta().hash();
        CachedThumb cached = cache.get(hash);
        if (cached != null) return cached.texture;

        Identifier texId = MoudTextures.resolve(entry.path().value());
        if (texId == null || TextureManager.MISSING_IDENTIFIER.equals(texId)) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        AbstractTexture mcTex = mc.getTextureManager().getOrDefault(texId, null);
        if (mcTex == null) return null;

        int glId = mcTex.getGlId();
        if (glId <= 0) return null;

        Texture miry = Texture.wrapExternal(glId, 1, 1, false);
        cache.put(hash, new CachedThumb(miry, glId));
        return miry;
    }

    static void invalidate(AssetHash hash) {
        cache.remove(hash);
    }

    static void clear() {
        cache.clear();
    }

    private record CachedThumb(Texture texture, int glId) {}
}
