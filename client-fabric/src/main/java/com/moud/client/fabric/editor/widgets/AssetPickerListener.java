package com.moud.client.fabric.editor.widgets;

@FunctionalInterface
public interface AssetPickerListener {
    void onBrowse(String typeFilter, String currentPath);
}
