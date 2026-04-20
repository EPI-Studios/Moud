package com.moud.server.minestom.scripting.runtime;


import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.scripting.ScriptCallable;
import com.moud.server.minestom.scripting.ScriptObject;
import com.moud.server.minestom.scripting.input.ScriptInputApi;
import com.moud.server.minestom.scripting.physics.CharacterBodySimulator;
import com.moud.server.minestom.scripting.player.PlayerNetworkSink;
import com.moud.server.minestom.scripting.player.PlayerStateManager;
import com.moud.server.minestom.scripting.scene.MultiMeshManager;
import com.moud.server.minestom.scripting.scene.SceneMutator;
import com.moud.server.minestom.scripting.signal.SignalBus;
import com.moud.server.minestom.script.ScriptMessageRouter;

import java.util.Map;
import java.util.UUID;

public interface RuntimeFacade {
    SceneMutator mutator();

    MultiMeshManager multiMeshManager();

    PlayerStateManager playerState();

    PlayerNetworkSink playerNetworkSink();

    TimerTweenScheduler scheduler();

    SignalBus signalBus();

    CharacterBodySimulator characterBodySimulator();

    Map<Long, ScriptObject> instanceValueMap();

    long createRuntimeNode(ServerScene scene, long parentId, String name, String typeId);

    void flush(ServerScene scene);

    void queueSceneTransition(String sceneId);

    String getPending(long nodeId, String key);

    ScriptCallable toScriptCallable(Object callback);

    long instantiateScene(ServerScene scene, String scenePath, long parentId);

    ScriptInputApi inputApiForNode(long nodeId);

    default ScriptMessageRouter scriptMessageRouter() { return null; }

    default Iterable<UUID> connectedPlayerUuids() { return java.util.List.of(); }

    default com.moud.server.minestom.persistence.PersistenceService persistence() { return null; }
}
