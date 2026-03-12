package com.moud.core.scene;

public final class Model3D extends Node {
    public static final String PROP_MODEL_PATH      = "model_path";
    public static final String PROP_ANIMATION       = "animation";
    public static final String PROP_ANIMATION_LOOP  = "animation_loop";
    public static final String PROP_ANIMATION_SPEED = "animation_speed";

    public Model3D(String name) {
        super(name);
    }
}
