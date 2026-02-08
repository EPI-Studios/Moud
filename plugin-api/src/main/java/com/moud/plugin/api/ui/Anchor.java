package com.moud.plugin.api.ui;

public enum Anchor {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    CENTER_LEFT("center_left"),
    CENTER_CENTER("center_center"),
    CENTER_RIGHT("center_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_CENTER("bottom_center"),
    BOTTOM_RIGHT("bottom_right");

    private final String wireName;

    Anchor(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
