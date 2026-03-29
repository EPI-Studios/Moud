package com.moud.client.fabric.render.hud;

import java.util.Map;
public final class ControlRenderers {

    private static final Map<String, ControlRenderer> REGISTRY = Map.ofEntries(
            Map.entry("Label",          LabelRenderer.INSTANCE),
            Map.entry("RichTextLabel",  RichTextLabelRenderer.INSTANCE),
            Map.entry("Button",         ButtonRenderer.INSTANCE),
            Map.entry("TextureButton",  TextureRectRenderer.INSTANCE),
            Map.entry("CheckBox",       CheckBoxRenderer.INSTANCE),
            Map.entry("ProgressBar",    ProgressBarRenderer.INSTANCE),
            Map.entry("HSlider",        SliderRenderer.HORIZONTAL),
            Map.entry("VSlider",        SliderRenderer.VERTICAL),
            Map.entry("LineEdit",       LineEditRenderer.INSTANCE),
            Map.entry("ColorRect",      ColorRectRenderer.INSTANCE),
            Map.entry("TextureRect",    TextureRectRenderer.INSTANCE),
            Map.entry("HBoxContainer",  ContainerRenderer.LAYOUT),
            Map.entry("VBoxContainer",  ContainerRenderer.LAYOUT),
            Map.entry("GridContainer",  ContainerRenderer.LAYOUT),
            Map.entry("MarginContainer",ContainerRenderer.WRAPPER),
            Map.entry("ScrollContainer",ContainerRenderer.WRAPPER),
            Map.entry("PanelContainer", ContainerRenderer.WRAPPER),
            Map.entry("Control",        ContainerRenderer.WRAPPER),
            Map.entry("CanvasItem",     ContainerRenderer.WRAPPER)
    );

    private static final ControlRenderer FALLBACK = ContainerRenderer.WRAPPER;

    private ControlRenderers() {}

    public static ControlRenderer get(String type) {
        return REGISTRY.getOrDefault(type, FALLBACK);
    }

    public static boolean isRegistered(String type) {
        return REGISTRY.containsKey(type);
    }
}
