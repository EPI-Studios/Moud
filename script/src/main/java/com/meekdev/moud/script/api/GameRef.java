package com.meekdev.moud.script.api;

public interface GameRef {

    boolean paused();

    void paused(boolean on);

    void quit();

    void openSettings();

    boolean exported();
}
