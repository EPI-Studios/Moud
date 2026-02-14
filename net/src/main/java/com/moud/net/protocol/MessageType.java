package com.moud.net.protocol;

public enum MessageType {
    HELLO(1),
    SERVER_HELLO(2),
    PING(3),
    PONG(4),
    SCENE_OP_BATCH(100),
    SCENE_OP_ACK(101),
    SCENE_SNAPSHOT_REQUEST(102),
    SCENE_SNAPSHOT(103),
    SCHEMA_SNAPSHOT(104),
    SCENE_LIST(105),
    SCENE_SELECT(106);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public static MessageType fromId(int id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageType id: " + id);
    }

    public int id() {
        return id;
    }
}
