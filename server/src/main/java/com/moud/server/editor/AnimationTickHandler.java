package com.moud.server.editor;


public final class AnimationTickHandler {
    public void tick(float deltaTimeSeconds) {
        AnimationManager.getInstance().tick(deltaTimeSeconds);
    }
}
