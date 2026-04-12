package com.moud.client.fabric.scripting.api;

public final class BodyApi {

    private BodyApiTarget target;

    public BodyApi() {
    }

    public BodyApi(BodyApiTarget target) {
        this.target = target;
    }

    public void setTarget(BodyApiTarget target) {
        this.target = target;
    }

    public float readFloat(String key) {
        if (target == null || key == null) return 0f;
        return target.getBodyFloat(key);
    }

    public void writeFloat(String key, float value) {
        if (target == null || key == null) return;
        // only allowed write-float keys
        switch (key) {
            case "velocity_x", "velocity_y", "velocity_z",
                 "speed", "acceleration", "deceleration",
                 "ground_friction", "air_control",
                 "jump_velocity", "gravity_scale",
                 "rotation_y", "rotation_x" -> target.setBodyFloat(key, value);
            default -> { /* silently ignore unknown write keys */ }
        }
    }

    public boolean readBool(String key) {
        if (target == null || key == null) return false;
        return target.getBodyBool(key);
    }

    public void writeBool(String key, boolean value) {
        if (target == null || key == null) return;
        if ("jump_requested".equals(key)) {
            target.setBodyBool(key, value);
        }
    }
}
