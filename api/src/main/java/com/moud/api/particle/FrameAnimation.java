package com.moud.api.particle;


public record FrameAnimation(int frames, float fps, boolean loop, boolean pingPong, int startFrame) {
}
