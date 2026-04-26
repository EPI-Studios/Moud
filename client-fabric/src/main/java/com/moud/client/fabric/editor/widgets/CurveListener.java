package com.moud.client.fabric.editor.widgets;

import java.util.List;

@FunctionalInterface
public interface CurveListener {
    void onChanged(List<CurvePoint> points);
}
