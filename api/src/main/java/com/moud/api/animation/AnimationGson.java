package com.moud.api.animation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public final class AnimationGson {
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private AnimationGson() {
    }

    public static Gson instance() {
        return GSON;
    }
}
