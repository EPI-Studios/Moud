package com.moud.client.fabric.editor.widgets;

import java.util.List;

public interface GradientListener {
    void onChanged(List<ColorStop> stops);

    default void onStopSelected(int index, ColorStop stop) { }
}
