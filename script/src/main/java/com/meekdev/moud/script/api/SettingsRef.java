package com.meekdev.moud.script.api;

import java.util.List;

public interface SettingsRef {

    List<String> volumes();

    double volume(String category);

    void volume(String category, double value);

    double fov();

    void fov(double degrees);

    boolean fullscreen();

    void fullscreen(boolean on);

    boolean vsync();

    void vsync(boolean on);

    int maxFps();

    void maxFps(int fps);

    int guiScale();

    void guiScale(int scale);

    int renderDistance();

    void renderDistance(int chunks);

    double sensitivity();

    void sensitivity(double value);

    List<String> actions();

    String keyOf(String action);

    void bind(String action, String key);

    void save();
}
