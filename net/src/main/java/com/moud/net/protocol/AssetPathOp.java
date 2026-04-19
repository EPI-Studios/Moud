package com.moud.net.protocol;

import java.util.Objects;

public record AssetPathOp(long requestId, Kind kind, String path, String newPath) implements Message {
    public enum Kind { CREATE_FOLDER, RENAME, DELETE_RECURSIVE }

    public AssetPathOp {
        Objects.requireNonNull(kind, "kind");
        if (path == null) path = "";
        if (newPath == null) newPath = "";
    }

    @Override public MessageType type() { return MessageType.ASSET_PATH_OP; }
}
