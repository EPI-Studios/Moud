package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import java.util.List;
import java.util.Locale;

public enum AssetKind {
    FOLDER("Folders", EditorIcon.FOLDER, List.of()),
    SCRIPT("Scripts", EditorIcon.SCRIPT, List.of(".luau", ".rv", ".java")),
    SCENE("Scenes", EditorIcon.PACKED_SCENE, List.of(".scene")),
    MODEL("Models", EditorIcon.MESH, List.of(".gltf", ".glb", ".bbmodel", ".ammesh")),
    ANIMATION("Animations", EditorIcon.ANIMATION, List.of(".anim")),
    TEXTURE("Textures", EditorIcon.TEXTURE_2D, List.of(".png", ".jpg", ".jpeg")),
    SOUND("Sounds", EditorIcon.AUDIO_STREAM, List.of(".ogg", ".wav", ".mp3", ".flac")),
    SHADER("Shaders", EditorIcon.SHADER, List.of(".glsl", ".vsh", ".fsh")),
    FONT("Fonts", EditorIcon.FILE, List.of(".ttf", ".otf")),
    OTHER("Other", EditorIcon.FILE, List.of());

    private final String label;
    private final EditorIcon icon;
    private final List<String> extensions;

    AssetKind(String label, EditorIcon icon, List<String> extensions) {
        this.label = label;
        this.icon = icon;
        this.extensions = extensions;
    }

    public String label() {
        return label;
    }

    public EditorIcon icon() {
        return icon;
    }

    public static AssetKind of(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (AssetKind kind : values()) {
            for (String extension : kind.extensions) {
                if (lower.endsWith(extension)) return kind;
            }
        }
        return OTHER;
    }
}
