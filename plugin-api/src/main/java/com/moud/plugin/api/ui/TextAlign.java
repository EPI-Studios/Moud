package com.moud.plugin.api.ui;

public enum TextAlign {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    private final String wireName;

    TextAlign(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
