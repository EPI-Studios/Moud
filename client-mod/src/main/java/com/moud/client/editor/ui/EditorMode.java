package com.moud.client.editor.ui;


public enum EditorMode {
    SCENE("Scene", "scene_icon"),
    ANIMATION("Animation", "animation_icon"),
    BLUEPRINT("Blueprint", "blueprint_icon"),
    PLAY("Play", "play_icon");

    private final String displayName;
    private final String iconKey;

    EditorMode(String displayName, String iconKey) {
        this.displayName = displayName;
        this.iconKey = iconKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconKey() {
        return iconKey;
    }
}
