package com.moud.net.protocol;

public sealed interface Message permits Hello, Ping, Pong, SceneList, SceneOpAck, SceneOpBatch, SceneSelect, SceneSnapshot, SceneSnapshotRequest, SchemaSnapshot, ServerHello {
    MessageType type();
}
