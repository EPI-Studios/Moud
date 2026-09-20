package com.meekdev.moud.mod.client.editor.project;

import java.util.Locale;

public enum HubIcon {
    CUBE,
    PUSH_PIN,
    MAGNIFYING_GLASS,
    CARET_DOWN,
    CARET_RIGHT,
    FOLDER_OPEN,
    LAYOUT,
    RECTANGLE_DASHED,
    PLUS;

    private static final String ROOT = "/assets/moud/editor/hub/";

    public String resourcePath() {
        return ROOT + name().toLowerCase(Locale.ROOT).replace('_', '-') + ".png";
    }
}
