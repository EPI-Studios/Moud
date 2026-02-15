package com.moud.server.minestom;

import com.moud.net.session.Session;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.PlayerConnection;

import java.util.UUID;

public final class EnginePlayer extends Player {
    private MinestomPlayerTransport transport;
    private Session session;
    private String activeSceneId = "main";
    private boolean schemaSent;
    private long scenesSentRevision = Long.MIN_VALUE;
    private boolean editorMode;

    public EnginePlayer(UUID uuid, String username, PlayerConnection playerConnection) {
        super(uuid, username, playerConnection);
    }

    public MinestomPlayerTransport transport() {
        return transport;
    }

    public void setTransport(MinestomPlayerTransport transport) {
        this.transport = transport;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String activeSceneId() {
        return activeSceneId;
    }

    public void setActiveSceneId(String activeSceneId) {
        this.activeSceneId = activeSceneId;
    }

    public boolean schemaSent() {
        return schemaSent;
    }

    public void markSchemaSent() {
        this.schemaSent = true;
    }

    public long scenesSentRevision() {
        return scenesSentRevision;
    }

    public void setScenesSentRevision(long scenesSentRevision) {
        this.scenesSentRevision = scenesSentRevision;
    }

    public boolean editorMode() {
        return editorMode;
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
    }
}
