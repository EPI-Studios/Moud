package com.meekdev.moud.script.api;

public interface InputRef {

    boolean down(String action);

    double mouseX();

    double mouseY();

    double mouseDeltaX();

    double mouseDeltaY();

    double screenWidth();

    double screenHeight();

    boolean known(String action);

    boolean mouseLocked();

    void lockMouse(boolean locked);

    double sensitivity();

    void sensitivity(double value);
}
