package com.moud.server.minestom.scripting.java;

import com.moud.server.minestom.scripting.api.CoreScriptApi;
import com.moud.server.minestom.scripting.api.modules.CameraApi;
import com.moud.server.minestom.scripting.api.modules.CursorApi;
import com.moud.server.minestom.scripting.api.modules.HttpApi;
import com.moud.server.minestom.scripting.api.modules.MessagingApi;
import com.moud.server.minestom.scripting.api.modules.NodeApi;
import com.moud.server.minestom.scripting.api.modules.ParticlesApi;
import com.moud.server.minestom.scripting.api.modules.PersistApi;
import com.moud.server.minestom.scripting.api.modules.PhysicsApi;
import com.moud.server.minestom.scripting.api.modules.PlayerApi;
import com.moud.server.minestom.scripting.api.modules.SceneApi;
import com.moud.server.minestom.scripting.player.InputEvent;

public abstract class NodeScript {
    protected CoreScriptApi core;
    protected NodeApi node;
    protected SceneApi scene;
    protected PhysicsApi physics;
    protected PlayerApi players;
    protected CameraApi camera;
    protected CursorApi cursor;
    protected MessagingApi msg;
    protected ParticlesApi particles;
    protected PersistApi persist;
    protected HttpApi http;

    public void onReady() {
    }

    public void onEnterTree() {
    }

    public void onExitTree() {
    }

    public void onProcess(double dt) {
    }

    public void onPhysicsProcess(double dt) {
    }

    public void onInput(InputEvent event) {
    }

    public void onSignal(String name, Object value) {
    }

    public final long selfId() {
        return core == null ? 0L : core.id();
    }

    public final void log(String message) {
        if (core != null) {
            core.log(message);
        }
    }

    final void bindCoreApi(CoreScriptApi api) {
        this.core = api;
        this.node = api.node();
        this.scene = api.scene();
        this.physics = api.physics();
        this.players = api.player();
        this.camera = api.camera();
        this.cursor = api.cursor();
        this.msg = api.msg();
        this.particles = api.particles();
        this.persist = api.persist();
        this.http = api.http();
    }
}
