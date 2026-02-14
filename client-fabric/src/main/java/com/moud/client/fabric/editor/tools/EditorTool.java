package com.moud.client.fabric.editor.tools;

public enum EditorTool {
    SELECT("SEL"),
    MOVE("MOV"),
    ROTATE("ROT"),
    SCALE("SCL");

    public final String shortName;

    EditorTool(String shortName) {
        this.shortName = shortName;
    }
}

