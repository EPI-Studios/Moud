package com.moud.client.fabric.editor.widgets;

@FunctionalInterface
public interface Vec3Listener {
    void onChanged(int axis, float x, float y, float z);
}
