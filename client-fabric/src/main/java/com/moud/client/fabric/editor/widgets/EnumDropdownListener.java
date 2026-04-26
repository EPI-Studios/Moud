package com.moud.client.fabric.editor.widgets;

@FunctionalInterface
public interface EnumDropdownListener {
    void onSelected(int index, String value);
}
