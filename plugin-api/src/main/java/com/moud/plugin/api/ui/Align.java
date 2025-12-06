package com.moud.plugin.api.ui;

public enum Align {
    FLEX_START("flex-start"),
    CENTER("center"),
    FLEX_END("flex-end"),
    STRETCH("stretch");

    private final String wireName;

    Align(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
