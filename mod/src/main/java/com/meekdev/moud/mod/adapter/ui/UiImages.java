package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.client.ClientPlace;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public final class UiImages {

    private static final Map<String, Integer> KNOWN = new HashMap<>();

    private UiImages() {}

    public static int texture(String src) {
        Integer known = KNOWN.get(src);
        if (known != null && known != 0) return known;
        Identifier id;
        if (src.startsWith(Res.SCHEME)) {
            Path root = ClientPlace.root();
            if (root == null) return 0;
            id = ImportedTextures.idForPath(root.resolve(Res.parse(src)).toAbsolutePath().toString());
        } else {
            id = Identifier.tryParse(src);
        }
        if (id == null) return 0;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        int gl = texture != null && texture.getTexture() instanceof GlTexture glTexture ? glTexture.glId() : 0;
        KNOWN.put(src, gl);
        return gl;
    }
}
