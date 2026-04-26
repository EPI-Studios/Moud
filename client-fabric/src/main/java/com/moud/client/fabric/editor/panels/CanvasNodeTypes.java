package com.moud.client.fabric.editor.panels;

import java.util.Set;

public final class CanvasNodeTypes {
    public static final Set<String> CANVAS_2D_TYPES = Set.of(
            "Node2D", "Camera2D",
            "CanvasItem", "Control", "CanvasLayer",
            "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer",
            "Label", "RichTextLabel", "TextureRect", "AnimatedTextureRect", "ColorRect", "ProgressBar",
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    public static final Set<String> CONTROL_TYPES = Set.of(
            "CanvasItem", "Control",
            "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer",
            "Label", "RichTextLabel", "TextureRect", "AnimatedTextureRect", "ColorRect", "ProgressBar",
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    private CanvasNodeTypes() {
    }
}
