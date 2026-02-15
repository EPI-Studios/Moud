package com.moud.net.protocol;

public sealed interface Message permits
        Hello,
        Ping,
        Pong,
        SceneList,
        SceneOpAck,
        SceneOpBatch,
        SceneSelect,
        SceneSnapshot,
        SceneSnapshotRequest,
        SchemaSnapshot,
        ServerHello,
        AssetManifestRequest,
        AssetManifestResponse,
        AssetUploadBegin,
        AssetUploadAck,
        AssetUploadChunk,
        AssetUploadComplete,
        AssetDownloadRequest,
        AssetDownloadBegin,
        AssetDownloadChunk,
        AssetDownloadComplete,
        PlayerInput,
        RuntimeState {
    MessageType type();
}
