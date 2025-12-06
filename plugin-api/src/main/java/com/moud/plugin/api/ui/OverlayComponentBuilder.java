package com.moud.plugin.api.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OverlayComponentBuilder {
    private String id;
    private String type;
    private String parentId;
    private final Map<String, Object> props = new HashMap<>();

    private OverlayComponentBuilder(String type, String id) {
        this.type = type;
        this.id = id;
    }

    public static OverlayComponentBuilder container(String id) {
        return new OverlayComponentBuilder("container", id);
    }

    public static OverlayComponentBuilder text(String id, String content) {
        return new OverlayComponentBuilder("text", id).text(content);
    }

    public static OverlayComponentBuilder image(String id, String source) {
        return new OverlayComponentBuilder("image", id).prop("source", source);
    }

    public static OverlayComponentBuilder button(String id, String label) {
        return new OverlayComponentBuilder("button", id).text(label);
    }

    public OverlayComponentBuilder id(String id) {
        this.id = id;
        return this;
    }

    public OverlayComponentBuilder type(String type) {
        this.type = type;
        return this;
    }

    public OverlayComponentBuilder parent(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public OverlayComponentBuilder pos(int x, int y) {
        props.put("x", x);
        props.put("y", y);
        return this;
    }

    public OverlayComponentBuilder width(int width) {
        props.put("width", width);
        return this;
    }

    public OverlayComponentBuilder height(int height) {
        props.put("height", height);
        return this;
    }

    public OverlayComponentBuilder size(int width, int height) {
        return width(width).height(height);
    }

    public OverlayComponentBuilder background(String color) {
        props.put("background", color);
        return this;
    }

    public OverlayComponentBuilder text(String text) {
        props.put("text", text);
        return this;
    }

    public OverlayComponentBuilder textColor(String color) {
        props.put("textColor", color);
        return this;
    }

    public OverlayComponentBuilder textAlign(TextAlign align) {
        if (align != null) {
            props.put("textAlign", align.wireName());
        }
        return this;
    }

    public OverlayComponentBuilder padding(int all) {
        props.put("padding", java.util.List.of(all, all, all, all));
        return this;
    }

    public OverlayComponentBuilder padding(int top, int right, int bottom, int left) {
        props.put("padding", java.util.List.of(top, right, bottom, left));
        return this;
    }

    public OverlayComponentBuilder border(int width, String color) {
        props.put("borderWidth", width);
        props.put("borderColor", color);
        return this;
    }

    public OverlayComponentBuilder opacity(double opacity) {
        props.put("opacity", opacity);
        return this;
    }

    public OverlayComponentBuilder scale(double factor) {
        props.put("scale", Map.of("x", factor, "y", factor));
        return this;
    }

    public OverlayComponentBuilder scale(double x, double y) {
        props.put("scale", Map.of("x", x, "y", y));
        return this;
    }

    public OverlayComponentBuilder anchor(Anchor anchor) {
        if (anchor != null) {
            props.put("anchor", anchor.wireName());
        }
        return this;
    }

    public OverlayComponentBuilder justifyContent(Justify justify) {
        if (justify != null) {
            props.put("justifyContent", justify.wireName());
        }
        return this;
    }

    public OverlayComponentBuilder alignItems(Align align) {
        if (align != null) {
            props.put("alignItems", align.wireName());
        }
        return this;
    }

    public OverlayComponentBuilder gap(int gap) {
        props.put("gap", gap);
        return this;
    }

    public OverlayComponentBuilder fullscreen() {
        props.put("fullscreen", true);
        return this;
    }

    public OverlayComponentBuilder autoResize(boolean autoResize) {
        props.put("autoResize", autoResize);
        return this;
    }

    public OverlayComponentBuilder prop(String key, Object value) {
        if (key != null && value != null) {
            props.put(key, value);
        }
        return this;
    }

    public UIOverlayDefinition build() {
        String resolvedId = (id == null || id.isBlank()) ? "ui_" + UUID.randomUUID() : id;
        return new UIOverlayDefinition(resolvedId, type, parentId, props);
    }
}
