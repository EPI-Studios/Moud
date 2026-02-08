package com.moud.server.network.sync;

import java.util.List;

public interface SyncableObject {
    long getId();

    List<Object> snapshotPackets();
}

