package com.moud.server.state;

import com.moud.api.math.Vector3;

import java.util.Objects;

public class BodyPart {
    private final String name;
    private Vector3 position;
    private Vector3 rotation;
    private Vector3 scale;
    private boolean visible;
    private boolean overrideAnimation;
    private InterpolationSettings interpolation;

    public BodyPart(String name) {
        this.name = name;
        this.position = Vector3.zero();
        this.rotation = Vector3.zero();
        this.scale = new Vector3(1, 1, 1);
        this.visible = true;
        this.overrideAnimation = false;
        this.interpolation = new InterpolationSettings();
    }

    public String getName() {
        return name;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public Vector3 getRotation() {
        return rotation;
    }

    public void setRotation(Vector3 rotation) {
        this.rotation = rotation;
    }

    public Vector3 getScale() {
        return scale;
    }

    public void setScale(Vector3 scale) {
        this.scale = scale;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isOverrideAnimation() {
        return overrideAnimation;
    }

    public void setOverrideAnimation(boolean overrideAnimation) {
        this.overrideAnimation = overrideAnimation;
    }

    public InterpolationSettings getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(InterpolationSettings interpolation) {
        this.interpolation = interpolation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BodyPart bodyPart = (BodyPart) o;
        return visible == bodyPart.visible &&
                overrideAnimation == bodyPart.overrideAnimation &&
                name.equals(bodyPart.name) &&
                position.equals(bodyPart.position) &&
                rotation.equals(bodyPart.rotation) &&
                scale.equals(bodyPart.scale) &&
                interpolation.equals(bodyPart.interpolation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, position, rotation, scale, visible, overrideAnimation, interpolation);
    }

    public static class InterpolationSettings {
        private boolean enabled;
        private long duration;
        private String easing;
        private float speed;

        public InterpolationSettings() {
            this.enabled = false;
            this.duration = 0;
            this.easing = "linear";
            this.speed = 1.0f;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public String getEasing() {
            return easing;
        }

        public void setEasing(String easing) {
            this.easing = easing;
        }

        public float getSpeed() {
            return speed;
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InterpolationSettings that = (InterpolationSettings) o;
            return enabled == that.enabled &&
                    duration == that.duration &&
                    Float.compare(that.speed, speed) == 0 &&
                    easing.equals(that.easing);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, duration, easing, speed);
        }
    }
}
