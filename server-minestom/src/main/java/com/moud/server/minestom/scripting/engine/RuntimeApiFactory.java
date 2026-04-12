package com.moud.server.minestom.scripting.engine;

import com.moud.server.minestom.engine.ServerScene;

public interface RuntimeApiFactory {
    Object create(ServerScene scene, long nodeId);
}
