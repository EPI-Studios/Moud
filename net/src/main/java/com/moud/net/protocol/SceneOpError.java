package com.moud.net.protocol;

public enum SceneOpError {
    NONE(0),
    NOT_FOUND(1),
    ALREADY_EXISTS(2),
    INVALID(3),
    INTERNAL(4);

    private final int id;

    SceneOpError(int id) {
        this.id = id;
    }

    public static SceneOpError fromId(int id) {
        for (SceneOpError error : values()) {
            if (error.id == id) {
                return error;
            }
        }
        throw new IllegalArgumentException("Unknown SceneOpError id: " + id);
    }

    public int id() {
        return id;
    }
}
