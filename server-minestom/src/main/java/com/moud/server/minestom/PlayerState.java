package com.moud.server.minestom;

import com.moud.net.session.Session;
import com.moud.server.minestom.net.MinestomPlayerTransport;
import com.moud.server.minestom.scene.AoiSnapshotTracker;

final class PlayerState {
    MinestomPlayerTransport transport;
    Session session;
    String activeSceneId = "main";
    String activeInstanceId;
    String pendingReservedInstanceId;
    String pendingReservationToken;
    String queuedPlaceId;
    boolean inLimbo;
    boolean schemaSent;
    long scenesSentRevision = Long.MIN_VALUE;
    long sceneSnapshotSentRevision = Long.MIN_VALUE;
    double aoiCenterX;
    double aoiCenterY;
    double aoiCenterZ;
    boolean aoiCenterValid;
    final AoiSnapshotTracker aoiTracker = new AoiSnapshotTracker();
    boolean editorOpen;
    boolean multiMeshSent;
    boolean meshPublishSent;
    String collisionGeometrySentSceneId;
}
