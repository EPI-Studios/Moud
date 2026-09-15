package com.meekdev.moud.mod.client.editor.panel;

public interface Panel {

    String id();

    String title();

    void render();

    default int windowFlags() {
        return 0;
    }

    default String windowName() {
        return title() + "###" + id();
    }
}
