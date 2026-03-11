package com.moud.client.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.miry.graphics.Texture;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.IconSprite;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
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
        int[] dims = new int[2]; // [width, height]
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

        // Decode image dimensions on the calling thread (STB image decode is thread-safe)
        NativeImage img;
        try {
            img = NativeImage.read(java.nio.ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            System.err.println("[MoudIcons] failed to decode " + resourcePath + ": " + e.getMessage());
            return;
        }
        dims[0] = img.getWidth();
        dims[1] = img.getHeight();

        // GL upload must happen on the render thread
        Runnable upload = () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            TextureManager tm = mc != null ? mc.getTextureManager() : null;
            if (tm == null) {
                img.close();
                return;
            }

            String safeName = name.replace('/', '_').replace(' ', '_');
            Identifier id = Identifier.of("moud", "icons/" + safeName);
            NativeImageBackedTexture mcTex = new NativeImageBackedTexture(img);
            tm.registerTexture(id, mcTex);
            mcTex.upload();

            int glId = mcTex.getGlId();
            Texture miry = Texture.wrapExternal(glId, dims[0], dims[1], false);
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

    private record Entry(IconSprite sprite) {}
}
