package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.core.image.ImageStore;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class EditableTextures {

    private static final class Uploaded {
        final EditableImage image;
        final DynamicTexture texture;
        final Identifier id;
        final int width;
        final int height;
        int version = -1;

        Uploaded(EditableImage image, DynamicTexture texture, Identifier id) {
            this.image = image;
            this.texture = texture;
            this.id = id;
            this.width = image.width();
            this.height = image.height();
        }
    }

    private static final Map<String, Uploaded> UPLOADED = new HashMap<>();
    private static int next;

    private EditableTextures() {}

    public static int gl(String source) {
        EditableImage image = ImageStore.find(source);
        Uploaded uploaded = UPLOADED.get(source);
        if (image == null) {
            if (uploaded != null) release(source, uploaded);
            return 0;
        }
        if (uploaded == null || uploaded.image != image || uploaded.width != image.width() || uploaded.height != image.height()) {
            if (uploaded != null) release(source, uploaded);
            NativeImage pixels = new NativeImage(image.width(), image.height(), false);
            DynamicTexture texture = new DynamicTexture(() -> "moud " + source, pixels);
            Identifier id = Identifier.fromNamespaceAndPath("moud", "editable/" + next++);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            uploaded = new Uploaded(image, texture, id);
            UPLOADED.put(source, uploaded);
        }
        if (uploaded.version != image.version()) {
            NativeImage pixels = uploaded.texture.getPixels();
            if (pixels == null) return 0;
            int[] argb = image.pixels();
            int w = image.width();
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < w; x++) pixels.setPixel(x, y, argb[y * w + x]);
            }
            uploaded.texture.upload();
            uploaded.version = image.version();
        }
        return uploaded.texture.getTexture() instanceof GlTexture texture ? texture.glId() : 0;
    }

    public static void sweep() {
        for (Iterator<Map.Entry<String, Uploaded>> it = UPLOADED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Uploaded> entry = it.next();
            if (ImageStore.find(entry.getKey()) == entry.getValue().image) continue;
            Minecraft.getInstance().getTextureManager().release(entry.getValue().id);
            it.remove();
        }
    }

    private static void release(String source, Uploaded uploaded) {
        Minecraft.getInstance().getTextureManager().release(uploaded.id);
        UPLOADED.remove(source);
    }
}
