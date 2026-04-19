package com.moud.net.protocol;

import java.util.Objects;

public record AssetPathOpAck(long requestId, AssetPathOp.Kind kind, boolean success,
                             String path, String newPath, int scenesUpdated, String error) implements Message {
    public AssetPathOpAck {
        Objects.requireNonNull(kind, "kind");
        if (path == null) path = "";
        if (newPath == null) newPath = "";
        if (error == null) error = "";
    }

    @Override public MessageType type() { return MessageType.ASSET_PATH_OP_ACK; }
}
