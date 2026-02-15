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
    SCENE_SELECT(106),
    ASSET_MANIFEST_REQUEST(200),
    ASSET_MANIFEST_RESPONSE(201),
    ASSET_UPLOAD_BEGIN(202),
    ASSET_UPLOAD_ACK(203),
    ASSET_UPLOAD_CHUNK(204),
    ASSET_UPLOAD_COMPLETE(205),
    ASSET_DOWNLOAD_REQUEST(206),
    ASSET_DOWNLOAD_BEGIN(207),
    ASSET_DOWNLOAD_CHUNK(208),
    ASSET_DOWNLOAD_COMPLETE(209),
    PLAYER_INPUT(300),
    RUNTIME_STATE(301);

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
