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
    SCENE_SAVE(107),
    SCENE_SAVE_ACK(108),
    SCENE_CREATE(109),
    SCENE_DELETE(110),
    SCENE_CREATE_ACK(111),
    SCENE_DELETE_ACK(112),
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
    RUNTIME_STATE(301),
    REQUEST_RESPAWN(302),
    EDITOR_MODE_CHANGED(303),
    UI_NODE_EVENT(304),
    MULTIMESH_DATA(305),
    PLAYER_MOTION(306),
    CURSOR_STATE(307),

    PROJECT_INFO_REQUEST(400),
    PROJECT_INFO(401),
    PROJECT_CREATE(402),
    PROJECT_CREATE_ACK(403),

    SCRIPT_ACTION_LIST_REQUEST(420),
    SCRIPT_ACTION_LIST_RESPONSE(421),
    SCRIPT_ACTION_INVOKE(422),
    SCRIPT_ACTION_INVOKE_ACK(423),

    SCRIPT_FILE_READ_REQUEST(424),
    SCRIPT_FILE_READ_RESPONSE(425),
    SCRIPT_FILE_WRITE_REQUEST(426),
    SCRIPT_FILE_WRITE_ACK(427);

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
