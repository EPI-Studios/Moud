package com.moud.plugin.api.services.model;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.plugin.api.models.ModelData;
import net.minestom.server.instance.Instance;

import java.util.Objects;

public record ModelDefinition(ModelData modelData,
                              Vector3 position,
                              Quaternion rotation,
                              Vector3 scale,
                              Instance instance) {

    public ModelDefinition(String modelPath,
                           Vector3 position,
                           Quaternion rotation,
                           Vector3 scale,
                           String texture,
                           Instance instance){
        this(new ModelData(modelPath, texture), position, rotation, scale, instance);
    }
    public ModelDefinition {
        Objects.requireNonNull(modelData, "modelData cannot be null");
        String modelPath = modelData.modelPath();
        Objects.requireNonNull(modelPath, "modelPath cannot be null");
        position = position == null ? new Vector3(0, 64, 0) : position;
        rotation = rotation == null ? Quaternion.identity() : rotation;
        scale = scale == null ? Vector3.one() : scale;
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
        private ModelData modelData;

        public Builder modelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder modelData(ModelData modelData) {
            this.modelData = modelData;
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
            if (modelData != null) return new ModelDefinition(modelData, position, rotation, scale, instance);
            return new ModelDefinition(modelPath, position, rotation, scale, texture, instance);
        }
    }
}
