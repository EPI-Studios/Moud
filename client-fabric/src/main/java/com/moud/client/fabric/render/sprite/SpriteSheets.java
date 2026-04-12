package com.moud.client.fabric.render.sprite;

import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.MoudTextures;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SpriteSheets {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpriteSheets.class);
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WARNED_ROTATED = new ConcurrentHashMap<>();

    private SpriteSheets() {
    }

    public static ResolvedFrame resolve(String spriteSheetRef,
                                        String textureOverrideRef,
                                        String animationName,
                                        boolean playing,
                                        boolean loop,
                                        float speedScale,
                                        int explicitFrame,
                                        long timeMs) {
        if (spriteSheetRef == null || spriteSheetRef.isBlank()) {
            return null;
        }

        String normalizedRef = spriteSheetRef.trim();
        long version = MoudTextAssets.versionOf(normalizedRef);
        CacheEntry cached = CACHE.get(normalizedRef);
        SpriteSheetAsset asset = cached != null && cached.version == version ? cached.asset : null;

        if (asset == null) {
            String json = MoudTextAssets.readText(normalizedRef);
            if (json == null || json.isBlank()) {
                asset = cached != null ? cached.asset : null;
            } else {
                try {
                    asset = SpriteSheetAsset.parse(json);
                    CACHE.put(normalizedRef, new CacheEntry(version, asset));
                } catch (Exception e) {
                    LOGGER.warn("[Moud] Failed to parse sprite sheet {}: {}", normalizedRef, e.getMessage());
                    return null;
                }
            }
        }

        if (asset == null) {
            return null;
        }

        SpriteSheetAsset.Frame frame = asset.frame(
                asset.animation(animationName),
                timeMs,
                playing,
                loop,
                speedScale,
                explicitFrame
        );
        if (frame == null) {
            return null;
        }

        if (frame.rotated()) {
            WARNED_ROTATED.computeIfAbsent(normalizedRef, key -> {
                LOGGER.warn("[Moud] Rotated sprite sheet frames are not supported yet: {}", key);
                return Boolean.TRUE;
            });
        }

        String textureRef = (textureOverrideRef != null && !textureOverrideRef.isBlank())
                ? textureOverrideRef.trim()
                : inferTextureRef(normalizedRef);
        Identifier textureId = MoudTextures.resolve(textureRef);
        if (textureId == null) {
            return null;
        }

        SpriteSheetAsset.Rect rect = frame.frameRect();
        float invW = 1.0f / asset.textureWidth();
        float invH = 1.0f / asset.textureHeight();
        return new ResolvedFrame(
                textureId,
                asset.textureWidth(),
                asset.textureHeight(),
                rect.x() * invW,
                rect.y() * invH,
                (rect.x() + rect.w()) * invW,
                (rect.y() + rect.h()) * invH,
                frame.spriteSourceRect().x(),
                frame.spriteSourceRect().y(),
                rect.w(),
                rect.h(),
                frame.sourceWidth(),
                frame.sourceHeight(),
                frame.rotated(),
                frame.trimmed()
        );
    }

    public static String inferTextureRef(String spriteSheetRef) {
        String trimmed = spriteSheetRef == null ? "" : spriteSheetRef.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(0, dot) + ".png" : trimmed + ".png";
    }

    private record CacheEntry(long version, SpriteSheetAsset asset) {}

    public record ResolvedFrame(
            Identifier textureId,
            int textureWidth,
            int textureHeight,
            float u0,
            float v0,
            float u1,
            float v1,
            int offsetX,
            int offsetY,
            int frameWidth,
            int frameHeight,
            int sourceWidth,
            int sourceHeight,
            boolean rotated,
            boolean trimmed
    ) {}
}
