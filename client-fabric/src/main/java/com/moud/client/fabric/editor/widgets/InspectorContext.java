package com.moud.client.fabric.editor.widgets;

import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

import java.util.Map;

public record InspectorContext(
        Ui ui,
        UiRenderer renderer,
        UiContext uiContext,
        UiInput input,
        Theme theme,
        long nodeId,
        Map<String, String> values,
        boolean interactive,
        InspectorBridge bridge
) {
    public void commit(String key, String encodedValue) {
        bridge.commitProperty(nodeId, key, encodedValue);
    }
}
