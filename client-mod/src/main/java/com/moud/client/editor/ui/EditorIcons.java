package com.moud.client.editor.ui;

import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class EditorIcons {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorIcons.class);
    private static final Map<String, Integer> iconTextures = new HashMap<>();
    private static boolean iconsLoaded = false;

    public static final String ICON_SCENE = "scene";
    public static final String ICON_ANIMATION = "animation";
    public static final String ICON_BLUEPRINT = "blueprint";
    public static final String ICON_PLAY = "play";

    public static final String ICON_TRANSLATE = "translate";
    public static final String ICON_ROTATE = "rotate";
    public static final String ICON_SCALE = "scale";

    public static final String ICON_MODEL = "model";
    public static final String ICON_PRIMITIVE = "primitive";
    public static final String ICON_LIGHT = "light";
    public static final String ICON_MARKER = "marker";
    public static final String ICON_CAMERA = "camera";
    public static final String ICON_ZONE = "zone";
    public static final String ICON_PLAYER = "player";
    public static final String ICON_DISPLAY = "display";

    private EditorIcons() {
    }

    public static void loadIcons() {
        if (iconsLoaded) {
            return;
        }

        LOGGER.info("Loading editor icons...");

        loadIcon(ICON_SCENE);
        loadIcon(ICON_ANIMATION);
        loadIcon(ICON_BLUEPRINT);
        loadIcon(ICON_PLAY);
        loadIcon(ICON_TRANSLATE);
        loadIcon(ICON_ROTATE);
        loadIcon(ICON_SCALE);
        loadIcon(ICON_MODEL);
        loadIcon(ICON_PRIMITIVE);
        loadIcon(ICON_LIGHT);
        loadIcon(ICON_MARKER);
        loadIcon(ICON_CAMERA);
        loadIcon(ICON_ZONE);
        loadIcon(ICON_PLAYER);
        loadIcon(ICON_DISPLAY);

        iconsLoaded = true;
        LOGGER.info("Loaded {} editor icons", iconTextures.size());
    }

    private static void loadIcon(String iconName) {
        Identifier identifier = Identifier.of("moud", "textures/editor/icons/" + iconName + ".png");
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.getTextureManager() == null) {
            LOGGER.warn("Cannot load icon {}: client not ready", iconName);
            return;
        }

        try {
            if (client.getResourceManager().getResource(identifier).isEmpty()) {
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            TextureManager textureManager = client.getTextureManager();
            AbstractTexture texture = textureManager.getTexture(identifier);

            if (!(texture instanceof ResourceTexture)) {
                textureManager.registerTexture(identifier, new ResourceTexture(identifier));
                texture = textureManager.getTexture(identifier);
            }

            if (texture.getGlId() == 0) {
                texture.bindTexture();
            }

            int glId = texture.getGlId();
            if (glId != 0) {
                iconTextures.put(iconName, glId);
                LOGGER.debug("Loaded icon: {} -> GL ID {}", iconName, glId);
            } else {
                LOGGER.warn("Failed to get GL ID for icon: {}", iconName);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error loading icon: {}", iconName, e);
        }
    }

    public static int getIcon(String key) {
        return iconTextures.getOrDefault(key, 0);
    }

    public static boolean hasIcon(String key) {
        return iconTextures.containsKey(key) && iconTextures.get(key) != 0;
    }

    public static void renderIcon(String key, String fallbackText, float size) {
        if (hasIcon(key)) {
            ImGui.image(getIcon(key), size, size, 0, 1, 1, 0);
        } else {
            ImGui.text(fallbackText);
        }
    }

    public static String getIconForType(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "model" -> ICON_MODEL;
            case "primitive" -> ICON_PRIMITIVE;
            case "light" -> ICON_LIGHT;
            case "marker" -> ICON_MARKER;
            case "camera" -> ICON_CAMERA;
            case "zone" -> ICON_ZONE;
            case "playermodel" -> ICON_PLAYER;
            case "display" -> ICON_DISPLAY;
            default -> null;
        };
    }
}
