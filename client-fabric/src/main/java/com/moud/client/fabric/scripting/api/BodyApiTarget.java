package com.moud.client.fabric.scripting.api;

public interface BodyApiTarget {
    float getBodyFloat(String key);
    void setBodyFloat(String key, float value);
    boolean getBodyBool(String key);
    void setBodyBool(String key, boolean value);
}
