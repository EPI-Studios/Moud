package com.meekdev.moud.mod.client.editor.style;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.kit.ToggleStyle;
import imgui.ImGui;

public final class IconWidgets {

    private static final float FULL_ALPHA = 1.0f;

    private final IconAtlas atlas;

    public IconWidgets(IconAtlas atlas) {
        this.atlas = atlas;
    }

    public long atlasTextureId(EditorIcon icon) {
        return atlas.textureId(icon);
    }

    public void draw(EditorIcon icon, float size) {
        ImGui.image(atlas.textureId(icon), size, size);
    }

    public void drawTinted(EditorIcon icon, float size, float red, float green, float blue) {
        ImGui.imageWithBg(atlas.textureId(icon), size, size, 0.0f, 0.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f, 0.0f, red, green, blue, FULL_ALPHA);
    }

    public void drawInline(EditorIcon icon, float size) {
        draw(icon, size);
        ImGui.sameLine();
    }

    public long imageId(String resourcePath) {
        return atlas.imageId(resourcePath);
    }

    public long logoTextureId() {
        return atlas.logoTextureId();
    }

    public long textureId(EditorIcon icon) {
        return atlas.textureId(icon);
    }

    public boolean iconButton(String id, EditorIcon icon, float size) {
        return ImGui.imageButton(id, atlas.textureId(icon), size, size);
    }

    public boolean toggleButton(String id, EditorIcon icon, float size, boolean active) {
        ToggleStyle.push(active, EditorMotion.valueOf(id));
        boolean clicked = iconButton(id, icon, size);
        ToggleStyle.pop(active);
        EditorMotion.towards(id, ImGui.isItemHovered());
        return clicked;
    }
}
