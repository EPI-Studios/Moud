package com.moud.client.ui.animation;

import com.moud.client.ui.component.UIComponent;
import org.graalvm.polyglot.Value;

public class UIAnimation {
    private final UIComponent component;
    private final float startX, startY, startWidth, startHeight, startOpacity;
    private final float targetX, targetY, targetWidth, targetHeight, targetOpacity;
    private final long startTime;
    private final long duration;
    private final EasingFunction easing;
    private final Value onComplete;

    private boolean running = true;
    private boolean hasX, hasY, hasWidth, hasHeight, hasOpacity;

    public UIAnimation(UIComponent component, AnimationConfig config) {
        this.component = component;
        this.startTime = System.currentTimeMillis();
        this.duration = config.duration;
        this.easing = config.easing;
        this.onComplete = config.onComplete;

        // Store start values
        this.startX = component.getX();
        this.startY = component.getY();
        this.startWidth = component.getWidth();
        this.startHeight = component.getHeight();
        this.startOpacity = (float) component.getOpacity();

        // Store target values if specified
        this.hasX = config.targetX != null;
        this.hasY = config.targetY != null;
        this.hasWidth = config.targetWidth != null;
        this.hasHeight = config.targetHeight != null;
        this.hasOpacity = config.targetOpacity != null;

        this.targetX = config.targetX != null ? config.targetX : startX;
        this.targetY = config.targetY != null ? config.targetY : startY;
        this.targetWidth = config.targetWidth != null ? config.targetWidth : startWidth;
        this.targetHeight = config.targetHeight != null ? config.targetHeight : startHeight;
        this.targetOpacity = config.targetOpacity != null ? config.targetOpacity : startOpacity;
    }

    public boolean update() {
        if (!running) return false;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            // animation complete
            if (hasX) component.setX(targetX);
            if (hasY) component.setY(targetY);
            if (hasWidth) component.setWidth(targetWidth);
            if (hasHeight) component.setHeight(targetHeight);
            if (hasOpacity) component.setOpacity(targetOpacity);

            if (onComplete != null && onComplete.canExecute()) {
                try {
                    onComplete.execute();
                } catch (Exception e) {
                    //
                }
            }

            running = false;
            return false;
        }

        // progress calculations
        float progress = (float) elapsed / duration;
        float easedProgress = easing.apply(progress);

        // values interpolation
        if (hasX) {
            float newX = lerp(startX, targetX, easedProgress);
            component.setX(newX);
        }
        if (hasY) {
            float newY = lerp(startY, targetY, easedProgress);
            component.setY(newY);
        }
        if (hasWidth) {
            float newWidth = lerp(startWidth, targetWidth, easedProgress);
            component.setWidth(newWidth);
        }
        if (hasHeight) {
            float newHeight = lerp(startHeight, targetHeight, easedProgress);
            component.setHeight(newHeight);
        }
        if (hasOpacity) {
            float newOpacity = lerp(startOpacity, targetOpacity, easedProgress);
            component.setOpacity(newOpacity);
        }

        return true;
    }

    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public static class AnimationConfig {
        public Float targetX;
        public Float targetY;
        public Float targetWidth;
        public Float targetHeight;
        public Float targetOpacity;
        public long duration = 300;
        public EasingFunction easing = EasingFunction.EASE_OUT;
        public Value onComplete;
    }
}
