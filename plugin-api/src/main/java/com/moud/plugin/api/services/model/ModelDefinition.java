package com.moud.plugin.api.services.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import net.minestom.server.instance.Instance;

import java.util.Objects;

public record ModelDefinition(String modelPath,
                              Vector3 position,
                              Quaternion rotation,
                              Vector3 scale,
                              String texture,
                              Instance instance) {

    public ModelDefinition {
        Objects.requireNonNull(modelPath, "modelPath");
        position = position == null ? new Vector3(0, 64, 0) : position;
        rotation = rotation == null ? Quaternion.identity() : rotation;
        scale = scale == null ? Vector3.one() : scale;
        texture = texture == null ? "" : texture;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelPath = "moud:models/capsule.obj";
        private Vector3 position = new Vector3(0, 64, 0);
        private Quaternion rotation = Quaternion.identity();
        private Vector3 scale = Vector3.one();
        private String texture = "";
        private Instance instance;

        public Builder modelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder position(Vector3 position) {
            this.position = position;
            return this;
        }

        public Builder rotation(Quaternion rotation) {
            this.rotation = rotation;
            return this;
        }

        public Builder scale(Vector3 scale) {
            this.scale = scale;
            return this;
        }

        public Builder texture(String texture) {
            this.texture = texture;
            return this;
        }

        public Builder instance(Instance instance) {
            this.instance = instance;
            return this;
        }

        public ModelDefinition build() {
            return new ModelDefinition(modelPath, position, rotation, scale, texture, instance);
        }
    }
}
