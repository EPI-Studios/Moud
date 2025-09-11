package com.moud.client.ui.animation;

import com.moud.client.ui.component.UIComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AnimationEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationEngine.class);
    private final List<Animation> activeAnimations = new CopyOnWriteArrayList<>();

    public void animate(UIComponent element, String property, Object from, Object to, int duration) {
        Animation animation = new Animation(element, property, from, to, duration);
        activeAnimations.add(animation);
    }

    public void update(float deltaTime) {
        if (activeAnimations.isEmpty()) {
            return;
        }
        activeAnimations.removeIf(animation -> {
            animation.update(deltaTime);
            return animation.isComplete();
        });
    }

    public void cleanup() {
        activeAnimations.clear();
    }

    private static class Animation {
        private final UIComponent element;
        private final String property;
        private final Object startValue;
        private final Object endValue;
        private final int duration;
        private float elapsed = 0f;
        private boolean complete = false;

        public Animation(UIComponent element, String property, Object from, Object to, int duration) {
            this.element = element;
            this.property = property;
            this.startValue = from;
            this.endValue = to;
            this.duration = duration;
        }

        public void update(float deltaTime) {
            if (complete) return;

            elapsed += deltaTime * 1000;
            float progress = Math.min(elapsed / duration, 1.0f);
            progress = easeInOutCubic(progress);

            updateProperty(progress);

            if (elapsed >= duration) {
                complete = true;
            }
        }

        private void updateProperty(float progress) {
            try {
                double start = ((Number) startValue).doubleValue();
                double end = ((Number) endValue).doubleValue();
                double current = start + (end - start) * progress;

                switch (property.toLowerCase()) {
                    case "x":
                        element.setPosition((int) current, element.getY());
                        break;
                    case "y":
                        element.setPosition(element.getX(), (int) current);
                        break;
                    case "width":
                        element.setSize((int) current, element.getHeight());
                        break;
                    case "height":
                        element.setSize(element.getWidth(), (int) current);
                        break;
                    case "opacity":
                        element.setOpacity(current);
                        break;
                    default:
                        LOGGER.warn("Unsupported animation property: {}", property);
                        complete = true;
                        break;
                }
            } catch (Exception e) {
                LOGGER.error("Error updating animation property '{}'", property, e);
                complete = true;
            }
        }

        private float easeInOutCubic(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        }

        public boolean isComplete() {
            return complete;
        }
    }
}