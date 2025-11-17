package com.moud.plugin.api.services.lighting;

import com.moud.api.math.Vector3;

import java.util.Objects;

public record PointLightDefinition(String type,
                                   Vector3 position,
                                   Vector3 direction,
                                   float red,
                                   float green,
                                   float blue,
                                   float brightness,
                                   float radius) {

    public PointLightDefinition {
        type = type == null ? "point" : type;
        position = position == null ? new Vector3(0, 64, 0) : position;
        direction = direction == null ? new Vector3(0, -1, 0) : direction;
        brightness = brightness <= 0 ? 1.0f : brightness;
        radius = radius <= 0 ? 8.0f : radius;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type = "point";
        private Vector3 position = new Vector3(0, 64, 0);
        private Vector3 direction = new Vector3(0, -1, 0);
        private float red = 1.0f;
        private float green = 1.0f;
        private float blue = 1.0f;
        private float brightness = 1.0f;
        private float radius = 16.0f;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder position(Vector3 position) {
            this.position = position;
            return this;
        }

        public Builder direction(Vector3 direction) {
            this.direction = direction;
            return this;
        }

        public Builder color(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            return this;
        }

        public Builder brightness(float brightness) {
            this.brightness = brightness;
            return this;
        }

        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }

        public PointLightDefinition build() {
            return new PointLightDefinition(type, position, direction, red, green, blue, brightness, radius);
        }
    }
}
