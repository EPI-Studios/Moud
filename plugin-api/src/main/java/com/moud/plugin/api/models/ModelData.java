package com.moud.plugin.api.models;

public record ModelData(String modelPath, String texturePath, String modelId) {

    public ModelData(String modelPath, String texturePath){
        this(modelPath, texturePath, modelPath);
    }

}
