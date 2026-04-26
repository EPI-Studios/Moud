package com.moud.client.fabric.editor.widgets;

public interface InspectorBridge {
    void commitProperty(long nodeId, String key, String encodedValue);
}
