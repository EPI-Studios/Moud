package com.moud.plugin.animation;

public interface AnimationScriptAPI {
    void play();
    void pause();
    void stop();
    void seek(float timeSeconds);
    void setSpeed(float speed);
    void setLoop(boolean loop);
    float getTime();
    float getDuration();
    boolean isPlaying();
    void onEvent(String eventName, Runnable callback);
    void onComplete(Runnable callback);
}
