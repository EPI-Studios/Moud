package com.moud.plugin.api.ui;

public enum Justify {
    FLEX_START("flex-start"),
    CENTER("center"),
    FLEX_END("flex-end"),
    SPACE_BETWEEN("space-between"),
    SPACE_AROUND("space-around");

    private final String wireName;

    Justify(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
