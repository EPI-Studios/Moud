package com.moud.plugin.api.models;

import java.util.Objects;

public record ModelData(String modelPath, String texturePath, String modelId) {

    public ModelData(String modelPath, String texturePath){
        this(modelPath, texturePath, modelPath);
    }

    public ModelData {
        Objects.requireNonNull(modelPath, "modelPath cannot be null");
        texturePath = texturePath == null ? "" : texturePath;
        modelId = modelId == null ? modelPath : modelId;
    }

}
