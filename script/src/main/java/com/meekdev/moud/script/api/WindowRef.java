package com.meekdev.moud.script.api;

import java.util.List;
import java.util.Map;

public interface WindowRef {

    String title();

    void title(String title);

    String icon();

    void icon(String res);

    boolean fullscreen();

    void fullscreen(boolean on);

    int width();

    int height();

    void resize(int width, int height);

    int x();

    int y();

    void moveTo(int x, int y);

    void center();

    boolean canMove();

    int minWidth();

    int minHeight();

    void minSize(int width, int height);

    boolean resizable();

    void resizable(boolean on);

    boolean focused();

    boolean minimized();

    double opacity();

    void opacity(double value);

    String cursor();

    void cursor(String cursor);

    boolean cursorVisible();

    void cursorVisible(boolean on);

    int fps();

    int displayWidth();

    int displayHeight();

    List<Map<String, Object>> monitors();

    void flash();

    void clipboard(String text);

    void preventClose();

    boolean visible();

    void visible(boolean on);
}
