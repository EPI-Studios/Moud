package com.moud.plugin.api.world;

import com.moud.api.math.Vector3;
import com.moud.plugin.api.ui.TextAlign;

public final class TextOptions {
    private Vector3 position = Vector3.zero();
    private String content = "";
    private String billboard = "fixed";
    private Integer lineWidth;
    private Boolean shadow;
    private Boolean seeThrough;
    private Integer backgroundColor;
    private Integer textOpacity;
    private TextAlign alignment;
    private Double hitboxWidth;
    private Double hitboxHeight;

    public Vector3 position() {
        return position;
    }

    public TextOptions position(Vector3 position) {
        this.position = position;
        return this;
    }

    public String content() {
        return content;
    }

    public TextOptions content(String content) {
        this.content = content;
        return this;
    }

    public String billboard() {
        return billboard;
    }

    public TextOptions billboard(String billboard) {
        this.billboard = billboard;
        return this;
    }

    public Integer lineWidth() {
        return lineWidth;
    }

    public TextOptions lineWidth(Integer lineWidth) {
        this.lineWidth = lineWidth;
        return this;
    }

    public Boolean shadow() {
        return shadow;
    }

    public TextOptions shadow(Boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public Boolean seeThrough() {
        return seeThrough;
    }

    public TextOptions seeThrough(Boolean seeThrough) {
        this.seeThrough = seeThrough;
        return this;
    }

    public Integer backgroundColor() {
        return backgroundColor;
    }

    public TextOptions backgroundColor(Integer backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public Integer textOpacity() {
        return textOpacity;
    }

    public TextOptions textOpacity(Integer textOpacity) {
        this.textOpacity = textOpacity;
        return this;
    }

    public TextAlign alignment() {
        return alignment;
    }

    public TextOptions alignment(TextAlign alignment) {
        this.alignment = alignment;
        return this;
    }

    public Double hitboxWidth() {
        return hitboxWidth;
    }

    public TextOptions hitboxWidth(Double hitboxWidth) {
        this.hitboxWidth = hitboxWidth;
        return this;
    }

    public Double hitboxHeight() {
        return hitboxHeight;
    }

    public TextOptions hitboxHeight(Double hitboxHeight) {
        this.hitboxHeight = hitboxHeight;
        return this;
    }
}
