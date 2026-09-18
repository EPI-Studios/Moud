package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.core.image.ImageStore;
import com.meekdev.moud.mod.adapter.gl.Textures;
import com.meekdev.moud.mod.adapter.render.EditableTextures;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.place.ImportSettings;
import com.meekdev.moud.mod.place.PlaceToml;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class UiImages {

    private static final Map<String, Integer> KNOWN = new HashMap<>();

    private UiImages() {}

    public record Image(Identifier id, int width, int height) {}

    public static @Nullable Image image(String res) {
        if (res.isEmpty() || texture(res) == 0) return null;
        Path root = ClientPlace.root();
        if (root == null) root = PlaceToml.root();
        Identifier id = ImportedTextures.idForPath(root.resolve(Res.parse(res)).toAbsolutePath().toString());
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || texture.getTexture() == null) return null;
        return new Image(id, texture.getTexture().getWidth(0), texture.getTexture().getHeight(0));
    }

    public static int @Nullable [] size(String src) {
        if (src.isEmpty()) return null;
        EditableImage editable = ImageStore.find(src);
        if (editable != null) return new int[] {editable.width(), editable.height()};
        if (src.startsWith(Res.SCHEME)) {
            Image image = image(src);
            return image == null ? null : new int[] {image.width(), image.height()};
        }
        Identifier id = Identifier.tryParse(src);
        AbstractTexture texture = id == null ? null : Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || texture.getTexture() == null) return null;
        return new int[] {texture.getTexture().getWidth(0), texture.getTexture().getHeight(0)};
    }

    public static int texture(String src) {
        if (ImageStore.isEditable(src)) return EditableTextures.gl(src);
        Integer known = KNOWN.get(src);
        if (known != null && known != 0) return known;
        Identifier id;
        boolean nearest = false;
        if (src.startsWith(Res.SCHEME)) {
            Path root = ClientPlace.root();
            if (root == null) root = PlaceToml.root();
            Path file = root.resolve(Res.parse(src));
            nearest = ImportSettings.of(file).nearest();
            id = ImportedTextures.idForPath(file.toAbsolutePath().toString());
        } else {
            id = Identifier.tryParse(src);
        }
        if (id == null) return 0;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        int gl = texture != null && texture.getTexture() instanceof GlTexture glTexture ? glTexture.glId() : 0;
        if (gl != 0 && nearest) Textures.filterNearest(gl);
        KNOWN.put(src, gl);
        return gl;
    }
}
