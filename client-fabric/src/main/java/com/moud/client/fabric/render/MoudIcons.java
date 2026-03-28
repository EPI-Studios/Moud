package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.miry.graphics.Texture;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.IconSprite;
import com.miry.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import org.lwjgl.system.MemoryUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MoudIcons {

    private static final Map<String, Entry> registry = new LinkedHashMap<>();

    private MoudIcons() {}

    public static void register(String name, IconSprite sprite) {
        if (name != null && sprite != null) {
            registry.put(name, new Entry(sprite));
        }
    }

    public static void loadFromResource(String name, String resourcePath) {
        if (name == null || resourcePath == null) return;

        byte[] bytes;
        try (InputStream is = MoudIcons.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[MoudIcons] resource not found: " + resourcePath);
                return;
            }
            bytes = is.readAllBytes();
        } catch (IOException e) {
            System.err.println("[MoudIcons] failed to read " + resourcePath + ": " + e.getMessage());
            return;
        }

        ByteBuffer direct = MemoryUtil.memAlloc(bytes.length);
        NativeImage img;
        try {
            direct.put(bytes).rewind();
            img = NativeImage.read(direct);
        } catch (Exception e) {
            System.err.println("[MoudIcons] failed to decode " + resourcePath + ": " + e);
            MemoryUtil.memFree(direct);
            return;
        }
        MemoryUtil.memFree(direct);
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        Runnable upload = () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            TextureManager tm = mc != null ? mc.getTextureManager() : null;
            if (tm == null) {
                img.close();
                System.err.println("[MoudIcons] TextureManager not available for " + resourcePath);
                return;
            }

            String safeName = name.replace('/', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
            Identifier id = Identifier.of("moud", "icons/" + safeName);
            NativeImageBackedTexture mcTex = new NativeImageBackedTexture(img);
            tm.registerTexture(id, mcTex);
            mcTex.upload();

            int glId = mcTex.getGlId();
            Texture miry = Texture.wrapExternal(glId, imgW, imgH, false);
            IconSprite sprite = new IconSprite(miry, 0f, 0f, 1f, 1f);
            registry.put(name, new Entry(sprite));
        };

        if (RenderSystem.isOnRenderThread()) {
            upload.run();
        } else {
            RenderSystem.recordRenderCall(upload::run);
        }
    }

    public static IconSprite get(String name) {
        Entry e = name != null ? registry.get(name) : null;
        return e != null ? e.sprite : null;
    }

    public static boolean has(String name) {
        return name != null && registry.containsKey(name);
    }

    public static void draw(UiRenderer r, String name, float x, float y, float size, int tintArgb) {
        if (r == null) return;
        IconSprite sprite = get(name);
        if (sprite == null || sprite.texture() == null) return;
        r.drawTexturedRect(sprite.texture(), x, y, size, size,
                sprite.u0(), sprite.v0(), sprite.u1(), sprite.v1(), tintArgb);
    }

    public static void drawCentered(UiRenderer r, String name,
                                    float bx, float by, float bw, float bh,
                                    float size, int tintArgb) {
        draw(r, name, bx + (bw - size) * 0.5f, by + (bh - size) * 0.5f, size, tintArgb);
    }

    public static void drawOrFallback(UiRenderer r, Theme theme, Icon fallback,
                                      float x, float y, float size, int color) {
        if (r == null) return;
        String key = fallback.name().toLowerCase(Locale.ROOT);
        if (has(key)) {
            draw(r, key, x, y, size, color);
        } else if (theme != null && theme.icons != null) {
            theme.icons.draw(r, fallback, x, y, size, color);
        }
    }

    private record Entry(IconSprite sprite) {}
}
