package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.image.ImageStore;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.adapter.render.EditableTextures;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

final class EffectTextures {

    record Sprite(int gl, boolean nearest) {}

    private static final int DOT_SIZE = 64;

    private static final Map<String, Identifier> KNOWN = new HashMap<>();
    private static Sprite white;
    private static Sprite dot;
    private static Sprite clear;

    private EffectTextures() {}

    static Sprite white() {
        if (white == null) white = new Sprite(Textures.upload(1, BufferUtils.createByteBuffer(4).put(new byte[] {-1, -1, -1, -1}).flip()), true);
        return white;
    }

    static Sprite of(String src) {
        if (src.isEmpty()) return dot();
        if (ImageStore.isEditable(src)) {
            int gl = EditableTextures.gl(src);
            return gl == 0 ? clear() : new Sprite(gl, false);
        }
        if (src.startsWith(Res.SCHEME)) {
            int gl = UiImages.texture(src);
            return gl == 0 ? dot() : new Sprite(gl, false);
        }
        Identifier id = KNOWN.computeIfAbsent(src, EffectTextures::find);
        if (id == null) return dot();
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        return texture != null && texture.getTexture() instanceof GlTexture gl ? new Sprite(gl.glId(), true) : dot();
    }

    private static Identifier find(String src) {
        Identifier id = Identifier.tryParse(src);
        if (id == null) return null;
        String path = id.getPath();
        if (!path.startsWith("textures/")) {
            path = (path.indexOf('/') < 0 ? "textures/particle/" : "textures/") + path;
        }
        if (!path.endsWith(".png")) path += ".png";
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    private static Sprite clear() {
        if (clear == null) clear = new Sprite(Textures.upload(1, BufferUtils.createByteBuffer(4).put(new byte[] {0, 0, 0, 0}).flip()), true);
        return clear;
    }

    private static Sprite dot() {
        if (dot != null) return dot;
        ByteBuffer pixels = BufferUtils.createByteBuffer(DOT_SIZE * DOT_SIZE * 4);
        double middle = (DOT_SIZE - 1) / 2.0;
        for (int y = 0; y < DOT_SIZE; y++) {
            for (int x = 0; x < DOT_SIZE; x++) {
                double distance = Math.hypot(x - middle, y - middle) / (DOT_SIZE / 2.0);
                double fade = Math.clamp(1 - distance, 0, 1);
                pixels.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) Math.round(fade * fade * 255));
            }
        }
        dot = new Sprite(Textures.upload(DOT_SIZE, pixels.flip()), false);
        return dot;
    }
}
