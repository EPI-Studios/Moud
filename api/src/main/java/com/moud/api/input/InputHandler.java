package com.moud.api.input;

public interface InputHandler {

    boolean isKeyPressed(int keyCode);
    boolean isKeyJustPressed(int keyCode);
    boolean isKeyJustReleased(int keyCode);

    boolean isMouseButtonPressed(int button);
    boolean isMouseButtonJustPressed(int button);
    boolean isMouseButtonJustReleased(int button);

    float getMouseX();
    float getMouseY();
    float getMouseDeltaX();
    float getMouseDeltaY();
    float getScrollDelta();

    void setMouseSensitivity(float sensitivity);
    float getMouseSensitivity();

    void lockMouse(boolean locked);
    boolean isMouseLocked();

    void registerKeyBinding(String name, int defaultKey, Runnable action);
    void registerKeyBinding(String name, int defaultKey, KeyAction action);

    void setKeyBinding(String name, int keyCode);
    int getKeyBinding(String name);

    void registerMouseAction(int button, MouseAction action);
    void registerScrollAction(ScrollAction action);

    @FunctionalInterface
    interface KeyAction {
        void onKey(boolean pressed, boolean justPressed, boolean justReleased);
    }

    @FunctionalInterface
    interface MouseAction {
        void onMouse(boolean pressed, boolean justPressed, boolean justReleased, float x, float y);
    }

    @FunctionalInterface
    interface ScrollAction {
        void onScroll(float delta, float x, float y);
    }
}