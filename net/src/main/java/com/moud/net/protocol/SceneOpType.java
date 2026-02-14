package com.moud.net.protocol;

public enum SceneOpType {
    CREATE_NODE(1),
    QUEUE_FREE(2),
    RENAME(3),
    SET_PROPERTY(4),
    REMOVE_PROPERTY(5),
    REPARENT(6);

    private final int id;

    SceneOpType(int id) {
        this.id = id;
    }

    public static SceneOpType fromId(int id) {
        for (SceneOpType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SceneOpType id: " + id);
    }

    public int id() {
        return id;
    }
}
