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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class UiImages {

    private static final Map<String, Identifier> KNOWN = new HashMap<>();
    private static final Set<String> NEAREST = new HashSet<>();
    private static final Set<Integer> FILTERED = new HashSet<>();

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
        Identifier id = KNOWN.computeIfAbsent(src, UiImages::resolve);
        AbstractTexture texture = id == null ? null : Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || texture.getTexture() == null) return null;
        return new int[] {texture.getTexture().getWidth(0), texture.getTexture().getHeight(0)};
    }

    public static int texture(String src) {
        if (ImageStore.isEditable(src)) return EditableTextures.gl(src);
        Identifier id = KNOWN.computeIfAbsent(src, UiImages::resolve);
        if (id == null) return 0;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        int gl = texture != null && texture.getTexture() instanceof GlTexture glTexture ? glTexture.glId() : 0;
        if (gl != 0 && NEAREST.contains(src) && FILTERED.add(gl)) Textures.filterNearest(gl);
        return gl;
    }

    private static @Nullable Identifier resolve(String src) {
        if (src.startsWith(Res.SCHEME)) {
            Path root = ClientPlace.root();
            if (root == null) root = PlaceToml.root();
            Path file = root.resolve(Res.parse(src));
            if (ImportSettings.of(file).nearest()) NEAREST.add(src);
            return ImportedTextures.idForPath(file.toAbsolutePath().toString());
        }
        Identifier id = Identifier.tryParse(src);
        if (id == null) return null;
        String path = id.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }
}
