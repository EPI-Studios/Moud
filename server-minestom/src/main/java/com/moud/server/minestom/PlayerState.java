package com.moud.server.minestom;

import com.moud.net.session.Session;
import com.moud.server.minestom.net.MinestomPlayerTransport;

final class PlayerState {
    MinestomPlayerTransport transport;
    Session session;
    String activeSceneId = "main";
    boolean schemaSent;
    long scenesSentRevision = Long.MIN_VALUE;
    boolean editorOpen;
    boolean multiMeshSent;
}
