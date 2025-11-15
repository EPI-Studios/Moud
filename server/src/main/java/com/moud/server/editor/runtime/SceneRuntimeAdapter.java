package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;

public interface SceneRuntimeAdapter {

    void create(MoudPackets.SceneObjectSnapshot snapshot) throws Exception;

    void update(MoudPackets.SceneObjectSnapshot snapshot) throws Exception;

    void remove();
}